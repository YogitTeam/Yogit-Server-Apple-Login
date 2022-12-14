package com.example.demo.util;

import com.example.demo.model.Key;
import com.example.demo.model.Keys;
import com.example.demo.model.TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.ReadOnlyJWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.minidev.json.JSONObject;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class AppleUtils {

    @Value("${APPLE.PUBLICKEY.URL}")
    private String APPLE_PUBLIC_KEYS_URL;

    @Value("${APPLE.ISS}")
    private String ISS;

    @Value("${APPLE.AUD}")
    private String AUD;

    @Value("${APPLE.TEAM.ID}")
    private String TEAM_ID;

    @Value("${APPLE.KEY.ID}")
    private String KEY_ID;

    @Value("${APPLE.KEY.PATH}")
    private String KEY_PATH;

    @Value("${APPLE.AUTH.TOKEN.URL}")
    private String AUTH_TOKEN_URL;

    @Value("${APPLE.WEBSITE.URL}")
    private String APPLE_WEBSITE_URL;

    /**
     * User??? Sign in with Apple ??????(https://appleid.apple.com/auth/authorize)?????? ???????????? id_token??? ????????? ?????? ??????
     * Apple Document URL ??? https://developer.apple.com/documentation/sign_in_with_apple/sign_in_with_apple_rest_api/verifying_a_user
     *
     * @param id_token
     * @return boolean
     */
    public boolean verifyIdentityToken(String id_token) {

        try {
            SignedJWT signedJWT = SignedJWT.parse(id_token);
            ReadOnlyJWTClaimsSet payload = signedJWT.getJWTClaimsSet();

            // EXP ???????????? ??????
            Date currentTime = new Date(System.currentTimeMillis());
            if (!currentTime.before(payload.getExpirationTime())) {
                return false;
            }

            // NONCE(Test value), ISS, AUD
            if (!"20B20D-0S8-1K8".equals(payload.getClaim("nonce")) || !ISS.equals(payload.getIssuer()) || !AUD.equals(payload.getAudience().get(0))) {
                return false;
            }

            // RSA
            if (verifyPublicKey(signedJWT)) {
                return true;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Apple Server?????? ?????? ?????? ????????? ?????? ??????
     *
     * @param signedJWT
     * @return
     */
    private boolean verifyPublicKey(SignedJWT signedJWT) {

        try {
            String publicKeys = HttpClientUtils.doGet(APPLE_PUBLIC_KEYS_URL);
            ObjectMapper objectMapper = new ObjectMapper();
            Keys keys = objectMapper.readValue(publicKeys, Keys.class);
            for (Key key : keys.getKeys()) {
                RSAKey rsaKey = (RSAKey) JWK.parse(objectMapper.writeValueAsString(key));
                RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
                JWSVerifier verifier = new RSASSAVerifier(publicKey);

                if (signedJWT.verify(verifier)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * client_secret ??????
     * Apple Document URL ??? https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
     *
     * @return client_secret(jwt)
     */
    public String createClientSecret() {

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY_ID).build();
        JWTClaimsSet claimsSet = new JWTClaimsSet();
        Date now = new Date();

        claimsSet.setIssuer(TEAM_ID);
        claimsSet.setIssueTime(now);
        claimsSet.setExpirationTime(new Date(now.getTime() + 3600000));
        claimsSet.setAudience(ISS);
        claimsSet.setSubject(AUD);

        SignedJWT jwt = new SignedJWT(header, claimsSet);

        try {
//            ECPrivateKey ecPrivateKey = new ECPrivateKeyImpl2(readPrivateKey());
//            JWSSigner jwsSigner = new ECDSASigner(ecPrivateKey.getS());
//
//            jwt.sign(jwsSigner);
            System.out.println("=====????????? ?????? 1=====");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(readPrivateKey());
            System.out.println("=====????????? ?????? 5=====");


            try{
                KeyFactory kf = KeyFactory.getInstance("EC");
                System.out.println("=====????????? ?????? 6=====");
                ECPrivateKey ecPrivateKey = (ECPrivateKey) kf.generatePrivate(spec);
                System.out.println("=====????????? ?????? 7=====");
                System.out.println("=====????????? ?????? 7.5====="+ecPrivateKey.getS());
                JWSSigner jwsSigner = new ECDSASigner(ecPrivateKey.getS());
                System.out.println("=====????????? ?????? 8=====");
                jwt.sign(jwsSigner);
                System.out.println("=====????????? ?????? 9=====");
            }catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }catch (InvalidKeySpecException e){
                e.printStackTrace();
            }

        }
//        catch (InvalidKeyException e) {
//            e.printStackTrace();
//        }
        catch (JOSEException e) {
            e.printStackTrace();
        }

        return jwt.serialize();
    }

    /**
     * ???????????? private key ??????
     *
     * @return Private Key
     */
    private byte[] readPrivateKey() {

        ClassPathResource resource = new ClassPathResource(KEY_PATH);


//        Resource resource = new ClassPathResource(KEY_PATH);
        byte[] content = null;
        System.out.println("=====????????? ?????? 2=====");
        try (Reader keyReader = new InputStreamReader(resource.getInputStream());

             PemReader pemReader = new PemReader(keyReader)) {
            {
                System.out.println("=====????????? ?????? 3=====");
                PemObject pemObject = pemReader.readPemObject();
                System.out.println("=====????????? ?????? 3.5=====");
                content = pemObject.getContent();
                System.out.println("=====????????? ?????? 4=====");
                System.out.println(content+"=====content=====");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return content;
    }

    /**
     * ????????? code ?????? Apple Server??? ?????? ??????
     * Apple Document URL ??? https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
     *
     * @return
     */
    public TokenResponse validateAuthorizationGrantCode(String client_secret, String code) {

        Map<String, String> tokenRequest = new HashMap<>();

        tokenRequest.put("client_id", AUD);
        tokenRequest.put("client_secret", client_secret);
        tokenRequest.put("code", code);
        tokenRequest.put("grant_type", "authorization_code");
        tokenRequest.put("redirect_uri", APPLE_WEBSITE_URL);

        return getTokenResponse(tokenRequest);
    }

    /**
     * ????????? refresh_token ?????? Apple Server??? ?????? ??????
     * Apple Document URL ??? https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
     *
     * @param client_secret
     * @param refresh_token
     * @return
     */
    public TokenResponse validateAnExistingRefreshToken(String client_secret, String refresh_token) {

        Map<String, String> tokenRequest = new HashMap<>();

        tokenRequest.put("client_id", AUD);
        tokenRequest.put("client_secret", client_secret);
        tokenRequest.put("grant_type", "refresh_token");
        tokenRequest.put("refresh_token", refresh_token);

        return getTokenResponse(tokenRequest);
    }

    /**
     * POST https://appleid.apple.com/auth/token
     *
     * @param tokenRequest
     * @return
     */
    private TokenResponse getTokenResponse(Map<String, String> tokenRequest) {

        try {
            System.out.println("======tokenRequest"+tokenRequest);
            String response = HttpClientUtils.doPost(AUTH_TOKEN_URL, tokenRequest);
            System.out.println("======response"+response);

            ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false); // null?????? ?????? ??????
            System.out.println("======objectMapper"+objectMapper);

            TokenResponse tokenResponse = objectMapper.readValue(response, TokenResponse.class);
            System.out.println("======tokenResponse"+tokenResponse);

            if (tokenRequest != null) {
                return tokenResponse;
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Apple Meta Value
     *
     * @return
     */
    public Map<String, String> getMetaInfo() {

        Map<String, String> metaInfo = new HashMap<>();

        metaInfo.put("CLIENT_ID", AUD);
        metaInfo.put("REDIRECT_URI", APPLE_WEBSITE_URL);
        metaInfo.put("NONCE", "20B20D-0S8-1K8"); // Test value

        return metaInfo;
    }

    /**
     * id_token??? decode?????? payload ??? ????????????
     *
     * @param id_token
     * @return
     */
    public JSONObject decodeFromIdToken(String id_token) {

        try {
            SignedJWT signedJWT = SignedJWT.parse(id_token);
            System.out.println("=====payload1"+signedJWT );
            ReadOnlyJWTClaimsSet getPayload = signedJWT.getJWTClaimsSet();
            System.out.println("=====payload2"+getPayload);
            ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
//            objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT); // null ??? ??????
//            objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
//            Payload payload = objectMapper.readValue(getPayload.toJSONObject().toJSONString(), Payload.class);
            JSONObject payload = objectMapper.readValue(getPayload.toJSONObject().toJSONString(), JSONObject.class);

            System.out.println("=====payload3"+payload );
            if (payload != null) {
                return payload;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
