package ru.vidtu.ias.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import ru.vidtu.ias.SharedIAS;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for Microsoft authentication system.<br>
 *
 * @author VidTu
 * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme">Reference</a>
 */
public class Auth {
    public static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String REDIRECT_URI = "http://localhost:59125";
    private static final boolean BLIND_SSL = Boolean.getBoolean("ias.blindSSL");
    private static final boolean NO_CUSTOM_SSL = Boolean.getBoolean("ias.noCustomSSL");
    public static final SSLContext FIXED_CONTEXT;
    static {
        SSLContext ctx = null;
        try {
            if (BLIND_SSL) {
                SharedIAS.LOG.warn("========== IAS: WARNING ==========");
                SharedIAS.LOG.warn("You've enabled 'ias.blindSSL' property.");
                SharedIAS.LOG.warn("(probably via JVM-argument '-Dias.blindSSL=true')");
                SharedIAS.LOG.warn("While this may fix some SSL problems, it's UNSAFE!");
                SharedIAS.LOG.warn("Do NOT use this option as a 'permanent solution to all problems',");
                SharedIAS.LOG.warn("nag the mod authors if any problems arrive:");
                SharedIAS.LOG.warn("https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher/issues");
                SharedIAS.LOG.warn("========== IAS: WARNING ==========");
                TrustManager blindManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        // NO-OP
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        // NO-OP
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                };
                ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[] { blindManager }, new SecureRandom());
                SharedIAS.LOG.warn("Blindly skipping SSL checks. (behavior: 'ias.blindSSL' property)");
            } else if (!NO_CUSTOM_SSL) {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                try (InputStream in = Auth.class.getResourceAsStream("/iasjavafix.jks")) {
                    ks.load(in, "iasjavafix".toCharArray());
                }
                TrustManagerFactory customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                customTmf.init(ks);
                TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                defaultTmf.init((KeyStore) null);
                List<X509TrustManager> managers = new ArrayList<>();
                managers.addAll(Arrays.stream(customTmf.getTrustManagers()).filter(tm -> tm instanceof X509TrustManager)
                        .map(tm -> (X509TrustManager) tm).collect(Collectors.toList()));
                managers.addAll(Arrays.stream(defaultTmf.getTrustManagers()).filter(tm -> tm instanceof X509TrustManager)
                        .map(tm -> (X509TrustManager) tm).collect(Collectors.toList()));
                TrustManager multiManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        CertificateException wrapper = new CertificateException("Unable to validate via any trust manager.");
                        for (X509TrustManager manager : managers) {
                            try {
                                manager.checkClientTrusted(chain, authType);
                                return;
                            } catch (Throwable t) {
                                wrapper.addSuppressed(t);
                            }
                        }
                        throw wrapper;
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        CertificateException wrapper = new CertificateException("Unable to validate via any trust manager.");
                        for (X509TrustManager manager : managers) {
                            try {
                                manager.checkServerTrusted(chain, authType);
                                return;
                            } catch (Throwable t) {
                                wrapper.addSuppressed(t);
                            }
                        }
                        throw wrapper;
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        List<X509Certificate> certificates = new ArrayList<>();
                        for (X509TrustManager manager : managers) {
                            certificates.addAll(Arrays.asList(manager.getAcceptedIssuers()));
                        }
                        return certificates.toArray(new X509Certificate[0]);
                    }
                };
                ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[] { multiManager }, new SecureRandom());
                SharedIAS.LOG.info("Using shared SSL context. (behavior: default; custom + default certificates)");
            } else {
                SharedIAS.LOG.warn("Not editing SSL context. (behavior: 'ias.noCustomSSL' property)");
            }
        } catch (Throwable t) {
            SharedIAS.LOG.error("Unable to init SSL context.", t);
        }
        FIXED_CONTEXT = ctx;
    }

    /**
     * Get Microsoft Access Token and Microsoft Refresh Token from Microsoft Authentication Code.
     *
     * @param code Code from user auth redirect
     * @return Pair of Microsoft Access Token and Microsoft Refresh Token
     * @throws Exception If something goes wrong
     */
    public static Map.@NotNull Entry<@NotNull String, @NotNull String> codeToToken(@NotNull String code) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://login.live.com/oauth20_token.srf").openConnection();
        if (FIXED_CONTEXT != null) conn.setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(("client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") + "&" +
                    "code=" + URLEncoder.encode(code, "UTF-8") + "&" +
                    "grant_type=authorization_code&" +
                    "redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") + "&" +
                    "scope=XboxLive.signin%20XboxLive.offline_access").getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("codeToToken response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("codeToToken response: " + conn.getResponseCode(), t);
                }
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
                return new AbstractMap.SimpleImmutableEntry<>(resp.get("access_token").getAsString(), resp.get("refresh_token").getAsString());
            }
        }
    }

    /**
     * Refresh Old Microsoft Refresh Token using the default mod client ID.
     *
     * @param refreshToken Microsoft Refresh Token
     * @return Pair of Microsoft Access Token and Microsoft Refresh Token
     * @throws Exception If something goes wrong
     */
    public static Map.@NotNull Entry<@NotNull String, @NotNull String> refreshToken(@NotNull String refreshToken) throws Exception {
        return refreshToken(refreshToken, CLIENT_ID);
    }

    /**
     * Refresh Old Microsoft Refresh Token using a custom client ID.
     *
     * @param refreshToken Microsoft Refresh Token
     * @param clientId     Client ID to use (use CLIENT_ID for default)
     * @return Pair of Microsoft Access Token and Microsoft Refresh Token
     * @throws Exception If something goes wrong
     */
    public static Map.@NotNull Entry<@NotNull String, @NotNull String> refreshToken(@NotNull String refreshToken, @NotNull String clientId) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://login.live.com/oauth20_token.srf").openConnection();
        if (FIXED_CONTEXT != null) conn.setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(("client_id=" + URLEncoder.encode(clientId, "UTF-8") + "&" +
                    "refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") + "&" +
                    "grant_type=refresh_token&" +
                    "redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") + "&" +
                    "scope=XboxLive.signin%20XboxLive.offline_access").getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("refreshToken response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("refreshToken response: " + conn.getResponseCode(), t);
                }
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
                return new AbstractMap.SimpleImmutableEntry<>(resp.get("access_token").getAsString(), resp.get("refresh_token").getAsString());
            }
        }
    }

    /**
     * Get XBL Token from Microsoft Access Token.
     *
     * @param authToken Microsoft Access Token
     * @return XBL Token
     * @throws Exception If something goes wrong
     */
    public static @NotNull String authXBL(@NotNull String authToken) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://user.auth.xboxlive.com/user/authenticate").openConnection();
        if (FIXED_CONTEXT != null) conn.setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            JsonObject req = new JsonObject();
            JsonObject reqProps = new JsonObject();
            reqProps.addProperty("AuthMethod", "RPS");
            reqProps.addProperty("SiteName", "user.auth.xboxlive.com");
            reqProps.addProperty("RpsTicket", "d=" + authToken);
            req.add("Properties", reqProps);
            req.addProperty("RelyingParty", "http://auth.xboxlive.com");
            req.addProperty("TokenType", "JWT");
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("authXBL response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("authXBL response: " + conn.getResponseCode(), t);
                }
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
                return resp.get("Token").getAsString();
            }
        }
    }

    /**
     * Get XSTS Token and XUI-UHS Userhash from XBL Token.
     *
     * @param xblToken XBL Token
     * @return Pair of XSTS Token and XUI-UHS Userhash
     * @throws Exception If something goes wrong
     */
    public static Map.@NotNull Entry<@NotNull String, @NotNull String> authXSTS(@NotNull String xblToken) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://xsts.auth.xboxlive.com/xsts/authorize").openConnection();
        if (FIXED_CONTEXT != null) conn.setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            JsonObject req = new JsonObject();
            JsonObject reqProps = new JsonObject();
            JsonArray userTokens = new JsonArray();
            userTokens.add(new JsonPrimitive(xblToken));
            reqProps.add("UserTokens", userTokens);
            reqProps.addProperty("SandboxId", "RETAIL");
            req.add("Properties", reqProps);
            req.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            req.addProperty("TokenType", "JWT");
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("authXSTS response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("authXSTS response: " + conn.getResponseCode(), t);
                }
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
                return new AbstractMap.SimpleImmutableEntry<>(resp.get("Token").getAsString(), resp.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString());
            }
        }
    }

    /**
     * Get Minecraft Access Token from XUI-UHS Userhash and XSTS Token.
     *
     * @param userHash  XUI-UHS Userhash
     * @param xstsToken XSTS Token
     * @return Minecraft Access Token
     * @throws Exception If something goes wrong
     */
    public static @NotNull String authMinecraft(@NotNull String userHash, @NotNull String xstsToken) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://api.minecraftservices.com/authentication/login_with_xbox").openConnection();
        if (FIXED_CONTEXT != null) conn.setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            JsonObject req = new JsonObject();
            req.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("authMinecraft response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("authMinecraft response: " + conn.getResponseCode(), t);
                }
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
                return resp.get("access_token").getAsString();
            }
        }
    }

    /**
     * Get Player UUID and Player Name from Minecraft Access Token.
     *
     * @param accessToken Minecraft Access Token
     * @return Pair of Player UUID and Player Name
     * @throws Exception If something goes wrong
     */
    public static Map.@NotNull Entry<@NotNull UUID, @NotNull String> getProfile(@NotNull String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
            try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("getProfile response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
            } catch (Throwable t) {
                throw new IllegalArgumentException("getProfile response: " + conn.getResponseCode(), t);
            }
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
            return new AbstractMap.SimpleImmutableEntry<>(UUID.fromString(resp.get("id").getAsString().replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")),
                    resp.get("name").getAsString());
        }
    }

    /**
     * Change the player name of a Minecraft account.
     *
     * @param accessToken Minecraft Access Token
     * @param newName     New player name
     * @return Pair of new UUID and new name from the updated profile
     * @throws Exception If something goes wrong (e.g. name taken, cooldown, invalid name)
     */
    public static Map.@NotNull Entry<@NotNull UUID, @NotNull String> changeName(@NotNull String accessToken, @NotNull String newName) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/name/" +
                URLEncoder.encode(newName, "UTF-8")).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestMethod("PUT");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
            throw new IllegalArgumentException(formatNameChangeError(conn.getResponseCode(), readErrorBody(conn), newName));
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
            return new AbstractMap.SimpleImmutableEntry<>(UUID.fromString(resp.get("id").getAsString().replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")),
                    resp.get("name").getAsString());
        }
    }

    private static @NotNull String readErrorBody(@NotNull HttpURLConnection conn) {
        InputStream stream = conn.getErrorStream();
        if (stream == null) return "";
        try (BufferedReader err = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return err.lines().collect(Collectors.joining("\n"));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static @NotNull String formatNameChangeError(int code, @NotNull String body, @NotNull String newName) {
        String detail = extractApiErrorMessage(body);
        String haystack = (detail + "\n" + body).toLowerCase(Locale.ROOT);

        if (haystack.contains("duplicate") || haystack.contains("taken") || haystack.contains("unavailable")) {
            return "Name '" + newName + "' is unavailable or already taken.";
        }
        if (haystack.contains("inappropriate") || haystack.contains("not allowed") || haystack.contains("profan")) {
            return "Name '" + newName + "' is not allowed by Minecraft's name filter.";
        }
        if (haystack.contains("invalid") || haystack.contains("illegal") || code == 400) {
            return "Invalid name. Names must be 3-16 characters and use only letters, numbers, and underscores.";
        }
        if (haystack.contains("name change") || haystack.contains("cooldown") || code == 403) {
            return "Name change not allowed. This account may still be on the 30-day name-change cooldown.";
        }
        if (!detail.isEmpty()) {
            return "Name change failed: " + detail;
        }
        if (!body.isEmpty()) {
            return "Name change failed with HTTP " + code + ": " + body;
        }
        return "Name change failed with HTTP " + code + ".";
    }

    private static @NotNull String extractApiErrorMessage(@NotNull String body) {
        if (body.trim().isEmpty()) return "";
        try {
            JsonObject obj = SharedIAS.GSON.fromJson(body, JsonObject.class);
            StringBuilder sb = new StringBuilder();
            appendJsonString(sb, obj, "error");
            appendJsonString(sb, obj, "errorMessage");
            appendJsonString(sb, obj, "message");
            appendJsonString(sb, obj, "path");
            return sb.toString().trim();
        } catch (Throwable ignored) {
            return body.trim();
        }
    }

    private static void appendJsonString(@NotNull StringBuilder sb, @NotNull JsonObject obj, @NotNull String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return;
        if (sb.length() > 0) sb.append(": ");
        sb.append(el.getAsString());
    }

    /**
     * Change the skin of a Minecraft account using a URL.
     *
     * @param accessToken Minecraft Access Token
     * @param skinUrl     URL of the skin PNG
     * @param slim        Whether the skin uses the slim (Alex) model
     * @throws Exception If something goes wrong
     */
    public static void changeSkinUrl(@NotNull String accessToken, @NotNull String skinUrl, boolean slim) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/skins").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            JsonObject req = new JsonObject();
            req.addProperty("variant", slim ? "slim" : "classic");
            req.addProperty("url", skinUrl);
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("changeSkin response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("changeSkin response: " + conn.getResponseCode(), t);
                }
            }
        }
    }

    /**
     * Change the skin of a Minecraft account by uploading a PNG file.
     *
     * @param accessToken Minecraft Access Token
     * @param skinFile    Path to the skin PNG file
     * @param slim        Whether the skin uses the slim (Alex) model
     * @throws Exception If something goes wrong
     */
    public static void changeSkinFile(@NotNull String accessToken, @NotNull java.io.File skinFile, boolean slim) throws Exception {
        String boundary = "----IASBoundary" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/skins").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            // variant field
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Disposition: form-data; name=\"variant\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write((slim ? "slim" : "classic").getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            // file field
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + skinFile.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            java.nio.file.Files.copy(skinFile.toPath(), out);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("changeSkinFile response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("changeSkinFile response: " + conn.getResponseCode(), t);
                }
            }
        }
    }

    /**
     * Get the list of capes available on this account.
     * Returns a list of cape entries, each with an "id" and "alias" field.
     *
     * @param accessToken Minecraft Access Token
     * @return List of cape JsonObjects with "id" and "alias" fields
     * @throws Exception If something goes wrong
     */
    public static @NotNull java.util.List<@NotNull JsonObject> getCapes(@NotNull String accessToken) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile").openConnection();
        if (FIXED_CONTEXT != null) conn.setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
            try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("getCapes response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
            } catch (Throwable t) {
                throw new IllegalArgumentException("getCapes response: " + conn.getResponseCode(), t);
            }
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            JsonObject resp = SharedIAS.GSON.fromJson(in.lines().collect(Collectors.joining("\n")), JsonObject.class);
            SharedIAS.LOG.info("Profile response for capes: " + resp.toString());
            java.util.List<JsonObject> capes = new ArrayList<>();
            if (resp.has("capes")) {
                for (com.google.gson.JsonElement el : resp.getAsJsonArray("capes")) {
                    capes.add(el.getAsJsonObject());
                }
            }
            SharedIAS.LOG.info("Found " + capes.size() + " capes.");
            return capes;
        }
    }

    /**
     * Set the active cape for a Minecraft account.
     *
     * @param accessToken Minecraft Access Token
     * @param capeId      The cape ID to activate (from getCapes)
     * @throws Exception If something goes wrong
     */
    public static void setCape(@NotNull String accessToken, @NotNull String capeId) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/capes/active").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("PUT");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            JsonObject req = new JsonObject();
            req.addProperty("capeId", capeId);
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("setCape response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("setCape response: " + conn.getResponseCode(), t);
                }
            }
        }
    }

    /**
     * Hide (remove) the active cape for a Minecraft account.
     *
     * @param accessToken Minecraft Access Token
     * @throws Exception If something goes wrong
     */
    public static void hideCape(@NotNull String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/capes/active").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        if (conn.getResponseCode() < 200 || conn.getResponseCode() > 299) {
            try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("hideCape response: " + conn.getResponseCode() + ", data: " + err.lines().collect(Collectors.joining("\n")));
            } catch (Throwable t) {
                throw new IllegalArgumentException("hideCape response: " + conn.getResponseCode(), t);
            }
        }
    }

    /**
     * Resolve UUID from name using Mojang API.
     *
     * @param name Player name
     * @return Resolved v4 UUID, v3 Offline UUID if it can't be resolved
     */
    public static @NotNull UUID resolveUUID(@NotNull String name) {
        try (InputStreamReader in = new InputStreamReader(new URL("https://api.mojang.com/users/profiles/minecraft/"
                + name).openStream(), StandardCharsets.UTF_8)) {
            return UUID.fromString(SharedIAS.GSON.fromJson(in, JsonObject.class).get("id").getAsString().replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Throwable ignored) {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }
    }
}
