package com.android.actor.fi;

import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.Callback;
import com.android.actor.utils.SPUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.StandardIntegrityManager;
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaTasksClient;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import fi.iki.elonen.NanoHTTPD;

public class FIEntryPoint {

    private static final String TAG = "FIEntryPoint";
    public static String sPackageName;
    private static long sPackageVersionCode;
    private static HttpServer sHttpServer;
    private static RecaptchaTasksClient sRecaptchaTasksClient;
    private static Map<String, String> sSiteKeys = new HashMap<>() {{
        put("a.test", "6LendvApAAAAAFR9xVbEx3fKLdLBnK6i1fhGK6F-");
        put("com.viber.voip", "6Le7yQ4jAAAAAJKn2hbQu_ydG6Hw2-27yvkfKdVJ");
    }};
    private static Map<String, Integer> sPorts = new HashMap<>() {{
        //put("a.test", 11111);
        //put("com.viber.voip", 11112);
        put("com.henrikherzig.playintegritychecker", 11113);
        //put("com.zhiliaoapp.musically", 11114);
        put("com.facebook.katana", 11115);
        put("com.einnovation.temu", 11116);
        put("com.twitter.android", 11117);
        put("com.zhiliaoapp.musically", 11118);
        put("com.instagram.android", 11119);
        put("com.reddit.frontpage", 11120);
        put("com.enflick.android.TextNow", 11121);
    }};

    public static Map<String, Long> EXPRESS_INTEGRITY_PACKAGES = new HashMap<>() {{
            put("com.einnovation.temu", 158704287764L);
            put("com.twitter.android", 49625052041L);
    }};
    private static StandardIntegrityManager.StandardIntegrityTokenProvider sTokenProvider;

    public static int getPort(String packageName) {
        return sPorts.getOrDefault(packageName, 0);
    }

    public static Set<String> getAllPackages() {
        return sPorts.keySet();
    }

    public static Set<String> getPackages() {
        return getAllPackages();
    }

    public static void main() {
        Logger.d(TAG, "FIEntryPoint.main()");
        sPackageName = ActApp.currentApplication().getPackageName();
        try {
            sPackageVersionCode = ActApp.currentApplication().getPackageManager().getPackageInfo(sPackageName, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        new Thread(() -> {
            try {
                int port = sPorts.getOrDefault(sPackageName, 0);
                if (port <= 0) {
                    throw new Exception("No port config for " + sPackageName);
                }
                Logger.d(TAG, "Start http server on " + port);
                sHttpServer = new HttpServer(port);
            } catch (Throwable e) {
                Logger.e(TAG, "Failed to start http server.", e);
            }
        }).start();

        if (EXPRESS_INTEGRITY_PACKAGES.containsKey(sPackageName)) {
            new Thread(() -> {
                try {
                    expressIntegrityWarmup();
                } catch (Throwable e) {
                    Logger.e(TAG, "Failed to warmup express integrity.", e);
                }
            }).start();
        }
    }

    private static void expressIntegrityWarmup() throws Throwable {
        if (sPackageName.equals("com.einnovation.temu")) {
            if (sPackageVersionCode == 29100) {
                Temu29100.expressIntegrityWarmup();
                return;
            } else if (sPackageVersionCode == 30200) {
                Temu30200.expressIntegrityWarmup();
                return;
            }
        }

        Thread.sleep(5000);
        while (true) {
            Logger.i(TAG, "Start warmup express integrity.");
            BlockingReference<Integer> blockingReference = new BlockingReference<>();
            StandardIntegrityManager standardIntegrityManager =
                    IntegrityManagerFactory.createStandard(ActApp.currentApplication());
            standardIntegrityManager.prepareIntegrityToken(
                            StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                                    .setCloudProjectNumber(EXPRESS_INTEGRITY_PACKAGES.get(sPackageName))
                                    .build())
                    .addOnSuccessListener(tokenProvider -> {
                        Logger.d(TAG, "tokenProvider " + tokenProvider);
                        sTokenProvider = tokenProvider;
                        blockingReference.put(3600);
                    })
                    .addOnFailureListener(e -> {
                        Logger.e("Failed to warmup express integrity, sleep a while and retry.", e);
                        blockingReference.put(5);
                    });

            int seconds = blockingReference.take();
            Thread.sleep(seconds * 1000);
        }
    }

    static class HttpServer extends NanoHTTPD {

        public HttpServer(int port) throws IOException {
            super(port);
            start(5000, false);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            JSONObject params = null;
            try {
                if (Method.POST.equals(session.getMethod())) {
                    Map<String, String> body = new HashMap<>();
                    session.parseBody(body);
                    Logger.d(TAG, "body.get(\"postData\") " + body.get("postData"));
                    params = JSON.parseObject(body.get("postData"));
                    Logger.d(TAG, "Http serve " + session.getMethod() + ", " + JSON.toJSONString(params, true));
                }
            } catch (Throwable e) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Can't parse body, " + e);
            }

            if (uri.equals("/pi")) {
                return newFixedLengthResponse(pi(params).toString());
            } else if (uri.equals("/re")) {
                return newFixedLengthResponse(re(params).toString());
            } else if (uri.equals("/keystore_generate_key_pair")) {
                return newFixedLengthResponse(keystoreGenerateKeyPair(params).toString());
            } else if (uri.equals("/keystore_sign")) {
                return newFixedLengthResponse(keystoreSign(params).toString());
            }
            return newFixedLengthResponse("");
        }

        private JSONObject pi(JSONObject params) {
            BlockingReference<JSONObject> blockingReference = new BlockingReference<>();
            String nonce = params.getString("nonce");
            String cloudProjectNumber = params.getString("cloud_project_number");

            Callback.C2<Boolean, String> callback = (success, str) -> {
                JSONObject jResult = new JSONObject();
                if (success) {
                    jResult.put("success", true);
                    jResult.put("token", str);
                } else {
                    jResult.put("success", false);
                    jResult.put("msg", str);
                }
                blockingReference.put(jResult);
            };
            if (params.getBooleanValue("is_express")) {
                piExpressRequest(nonce, cloudProjectNumber, callback);
            } else {
                piRequest(nonce, cloudProjectNumber, callback);
            }

            try {
                JSONObject jResult = blockingReference.take();
                return jResult;
            } catch (InterruptedException e) {
                Logger.e(TAG, "BlockingReference exception.", e);
                JSONObject jResult = new JSONObject();
                jResult.put("success", false);
                jResult.put("msg", e.toString());
                return jResult;
            }
        }

        private void piRequest(String nonce, String cloudProjectNumber, Callback.C2<Boolean, String> callback) {
            if (sPackageName.equals("com.instagram.android")) {
                if (sPackageVersionCode == 373511179) {
                    Instagram373511179.piRequest(nonce, cloudProjectNumber, callback);
                } else {
                    throw new NotImplementedException("Instagram " + sPackageVersionCode);
                }
                return;
            } else if (sPackageName.equals("com.enflick.android.TextNow")) {
                if (sPackageVersionCode == 14953) {
                    TextNow14953.piRequest(nonce, cloudProjectNumber, callback);
                } else {
                    throw new NotImplementedException("TextNow " + sPackageVersionCode);
                }
                return;
            }

            try {
                Logger.d(TAG, "piRequest nonce " + nonce + ", cloudProjectNumber " + cloudProjectNumber);
                IntegrityManager integrityManager = IntegrityManagerFactory.create(ActApp.currentApplication());
                IntegrityTokenRequest.Builder builder = IntegrityTokenRequest.builder().setNonce(nonce);
                if (!StringUtils.isEmpty(cloudProjectNumber)) {
                    builder.setCloudProjectNumber(Long.parseLong(cloudProjectNumber));
                }
                integrityManager.requestIntegrityToken(builder.build())
                        .addOnSuccessListener(response -> {
                            Logger.d(TAG, "onSuccess " + response);
                            Logger.d(TAG, " " + response.getClass());
                            Logger.d(TAG, " " + response.token());
                            callback.onResult(true, response.token());
                        })
                        .addOnFailureListener(e -> {
                            Logger.e(TAG, "onFailure", e);
                            callback.onResult(false, e.toString());
                        });
            } catch (Throwable e) {
                Logger.e(TAG, "Request exception.", e);
                callback.onResult(false, e.toString());
            }
        }

        private void piExpressRequest(String nonce, String cloudProjectNumber, Callback.C2<Boolean, String> callback) {
            if (sPackageName.equals("com.einnovation.temu")) {
                if (sPackageVersionCode == 29100) {
                    Temu29100.piExpressRequest(nonce, cloudProjectNumber, callback);
                    return;
                } else if (sPackageVersionCode == 30200) {
                    Temu30200.piExpressRequest(nonce, cloudProjectNumber, callback);
                    return;
                }
            }

            try {
                Logger.d(TAG, "piExpressRequest nonce " + nonce + ", cloudProjectNumber " + cloudProjectNumber);
                if (sTokenProvider == null) {
                    callback.onResult(false, "No token provider.");
                    return;
                }
                if (Long.parseLong(cloudProjectNumber) != EXPRESS_INTEGRITY_PACKAGES.get(sPackageName)) {
                    callback.onResult(false, "Cloud project number not match, " + cloudProjectNumber);
                    return;
                }
                Task<StandardIntegrityManager.StandardIntegrityToken> integrityTokenResponse =
                        sTokenProvider.request(
                                StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                                        .setRequestHash(nonce)
                                        .build());
                integrityTokenResponse
                        .addOnSuccessListener(response -> {
                            Logger.d(TAG, "onSuccess " + response);
                            Logger.d(TAG, " " + response.getClass());
                            Logger.d(TAG, " " + response.token());
                            callback.onResult(true, response.token());
                        })
                        .addOnFailureListener(e -> {
                            Logger.e(TAG, "onFailure", e);
                            callback.onResult(false, e.toString());
                        });
            } catch (Throwable e) {
                Logger.e(TAG, "Request exception.", e);
                callback.onResult(false, e.toString());
            }
        }

        private JSONObject re(JSONObject params) {
            BlockingReference<JSONObject> blockingReference = new BlockingReference<>();
            String action = params.getString("action");
            reRequest(action, (success, str) -> {
                JSONObject jResult = new JSONObject();
                if (success) {
                    jResult.put("success", true);
                    jResult.put("token", str);
                } else {
                    jResult.put("success", false);
                    jResult.put("msg", str);
                }
                blockingReference.put(jResult);
            });
            try {
                JSONObject jResult = blockingReference.take();
                return jResult;
            } catch (InterruptedException e) {
                Logger.e(TAG, "BlockingReference exception.", e);
                JSONObject jResult = new JSONObject();
                jResult.put("success", false);
                jResult.put("msg", e.toString());
                return jResult;
            }
        }

        private void reRequest(String action, Callback.C2<Boolean, String> callback) {
            String siteKey = sSiteKeys.get(sPackageName);
            if (siteKey == null) {
                callback.onResult(false, "No site key for " + sPackageName);
                return;
            }
            if (sRecaptchaTasksClient == null) {
                Recaptcha.getTasksClient(ActApp.currentApplication(), siteKey, 20000L)
                        .addOnSuccessListener(recaptchaTasksClient -> {
                            Logger.d(TAG, "getTasksClient onSuccess " + recaptchaTasksClient);
                            sRecaptchaTasksClient = recaptchaTasksClient;
                            reRequestAction(action, callback);
                        })
                        .addOnFailureListener(e -> {
                            Logger.e(TAG, "getTasksClient onFailure.", e);
                            callback.onResult(false, e.toString());
                        });
            } else {
                reRequestAction(action, callback);
            }
        }

        private void reRequestAction(String action, Callback.C2<Boolean, String> callback) {
            sRecaptchaTasksClient.executeTask(RecaptchaAction.custom(action), 20000L)
                    .addOnSuccessListener(token -> {
                        Logger.d(TAG, "executeTask onSuccess, " + token);
                        callback.onResult(true, token);
                    })
                    .addOnFailureListener(e -> {
                        Logger.e(TAG, "executeTask onFailure.", e);
                        callback.onResult(false, e.toString());
                    });
        }

        private JSONObject keystoreGenerateKeyPair(JSONObject params) {
            JSONObject jResult = new JSONObject();
            try {
                String ecGenParameterSpecName = params.getString("ec_gen_parameter_spec_name");
                AlgorithmParameterSpec algorithmParameterSpec = null;
                if (ecGenParameterSpecName != null) {
                    algorithmParameterSpec = new ECGenParameterSpec(ecGenParameterSpecName);
                }

                String keyAlias = "fi-" + System.currentTimeMillis() + "-" + System.nanoTime();
                KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyAlias, params.getIntValue("purposes"))
                        .setDigests(params.getJSONArray("digests").toArray(new String[0]))
                        .setCertificateNotBefore(new Date(params.getLongValue("certificate_not_before")))
                        .setCertificateNotAfter(new Date(params.getLongValue("certificate_not_after")))
                        .setUserAuthenticationRequired(params.getBooleanValue("user_authentication_required"))
                        .setAttestationChallenge(Base64.decode(params.getString("attestation_challenge"), 0))
                        .setIsStrongBoxBacked(params.getBooleanValue("strong_box_backed"));
                if (algorithmParameterSpec != null) {
                    builder.setAlgorithmParameterSpec(algorithmParameterSpec);
                }
                generator.initialize(builder.build());
                generator.generateKeyPair();

                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                Certificate[] certificates = keyStore.getCertificateChain(keyAlias);

                JSONArray jCertificates = new JSONArray();
                for (Certificate certificate : certificates) {
                    StringBuilder stringBuilder = new StringBuilder();
                    byte[] bytes = certificate.getEncoded();
                    stringBuilder.append("-----BEGIN CERTIFICATE-----\n");
                    stringBuilder.append(Base64.encodeToString(bytes, 0));
                    stringBuilder.append("-----END CERTIFICATE-----\n");
                    String str = stringBuilder.toString();
                    jCertificates.add(str);
                    Logger.v(TAG, "keystoreGenerateKeyPair " + keyAlias + " " + str);
                }
                jResult.put("certificates", jCertificates);
                jResult.put("key_alias", keyAlias);
                jResult.put("success", true);
            } catch (Throwable e) {
                Logger.stackTrace("keystoreGenerateKeyPair exception.", e);
                jResult.put("success", false);
                jResult.put("msg", e.toString());
            }
            return jResult;
        }

        private JSONObject keystoreSign(JSONObject params) {
            JSONObject jResult = new JSONObject();
            try {
                String keyAlias = params.getString("key_alias");
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                if (keyStore.containsAlias(keyAlias)) {
                    String algorithm = params.getString("algorithm");
                    byte[] bytes = Base64.decode(params.getString("bytes"), 0);
                    KeyStore.Entry entry = keyStore.getEntry(keyAlias, null);
                    Signature signature = Signature.getInstance(algorithm);
                    signature.initSign(((KeyStore.PrivateKeyEntry) entry).getPrivateKey());
                    signature.update(bytes);
                    bytes = signature.sign();

                    String signed = Base64.encodeToString(bytes, 0);
                    Logger.d(TAG, "signed " + signed);
                    jResult.put("signed", signed);
                    jResult.put("success", true);
                } else {
                    jResult.put("success", false);
                    jResult.put("msg", "No key alias " + keyAlias);
                }
            } catch (Throwable e) {
                Logger.stackTrace("keystoreSign exception.", e);
                jResult.put("success", false);
                jResult.put("msg", e.toString());
            }
            return jResult;
        }
    }
}
