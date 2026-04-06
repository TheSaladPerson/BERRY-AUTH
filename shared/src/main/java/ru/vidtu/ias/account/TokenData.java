package ru.vidtu.ias.account;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import ru.vidtu.ias.SharedIAS;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Data class for sharing tokens between users.
 * Encodes token data into a shareable format.
 *
 * @author Barry
 */
public class TokenData {
    private String accessToken;
    private String name;
    private UUID uuid;
    private long expiryTime;

    public TokenData(@NotNull String accessToken, @NotNull String name, @NotNull UUID uuid, long expiryTime) {
        this.accessToken = accessToken;
        this.name = name;
        this.uuid = uuid;
        this.expiryTime = expiryTime;
    }

    /**
     * Encode token data to a shareable string.
     *
     * @return Base64 encoded JSON string
     */
    public @NotNull String encode() {
        JsonObject json = new JsonObject();
        json.addProperty("token", accessToken);
        json.addProperty("name", name);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("expiry", expiryTime);
        json.addProperty("version", 1);

        String jsonStr = json.toString();
        return Base64.getEncoder().encodeToString(jsonStr.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode token data from a shareable string.
     *
     * @param encoded Base64 encoded JSON string
     * @return TokenData object
     * @throws Exception if decoding fails
     */
    public static @NotNull TokenData decode(@NotNull String encoded) throws Exception {
        try {
            String jsonStr = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            JsonObject json = SharedIAS.GSON.fromJson(jsonStr, JsonObject.class);

            if (!json.has("version") || json.get("version").getAsInt() != 1) {
                throw new IllegalArgumentException("Invalid or unsupported token format version");
            }

            return new TokenData(
                    json.get("token").getAsString(),
                    json.get("name").getAsString(),
                    UUID.fromString(json.get("uuid").getAsString()),
                    json.get("expiry").getAsLong()
            );
        } catch (Exception e) {
            throw new Exception("Failed to decode token data: " + e.getMessage(), e);
        }
    }

    public @NotNull String getAccessToken() {
        return accessToken;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    public long getExpiryTime() {
        return expiryTime;
    }
}