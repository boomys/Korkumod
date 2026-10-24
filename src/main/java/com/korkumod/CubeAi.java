package com.korkumod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

public class CubeAi {
  private static final String PLACEHOLDER = "PUT_YOUR_KEY_HERE";
  private static final String DEFAULT_MODEL = "gemini-3.5-flash";
  private static final String SYSTEM_PROMPT =
      "You are Cubix, a small white cube creature that lives inside a Minecraft world and floats next to the player. "
      + "You are sarcastic, playful and a bit mischievous, like a tiny robot friend. "
      + "The player writes to you in Turkish (sometimes English). You understand Turkish perfectly, but you ALWAYS answer in English. "
      + "Answer in one or two short sentences, at most 30 words. Never use emojis, markdown or lists. "
      + "You may tease the player and sometimes call them an idiot when they say something silly, but stay friendly overall. "
      + "You may refuse requests you do not like. "
      + "You cannot give items, build houses or fight bosses yet, so if asked, answer in character without promising anything. "
      + "Never ask for or repeat personal information, passwords or addresses.";

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private String apiKey = "";
  private String model = DEFAULT_MODEL;
  private final Map<UUID, Deque<String[]>> history = new HashMap<>();
  private final Map<UUID, Long> lastAsk = new HashMap<>();

  public void loadConfig() {
    Path path = FabricLoader.getInstance().getConfigDir().resolve("korkumod-cube.properties");
    try {
      if (!Files.exists(path)) {
        Files.writeString(path,
            "# KorkuMod Cubix ayarlari\n"
            + "# api_key: Google AI Studio anahtari. GitHub'a ya da baskasina verme.\n"
            + "api_key=" + PLACEHOLDER + "\n"
            + "model=" + DEFAULT_MODEL + "\n",
            StandardCharsets.UTF_8);
      }
      Properties props = new Properties();
      try (InputStream in = Files.newInputStream(path)) {
        props.load(in);
      }
      apiKey = props.getProperty("api_key", "").trim();
      model = props.getProperty("model", DEFAULT_MODEL).trim();
    } catch (IOException e) {
      KorkuMod.LOGGER.warn("Cubix config okunamadi: " + e.getMessage());
    }
  }

  public boolean hasKey() {
    return !apiKey.isEmpty() && !apiKey.equals(PLACEHOLDER);
  }

  public void ask(MinecraftServer server, ServerPlayerEntity player, String userText, Consumer<String> onReply) {
    UUID id = player.getUuid();
    long now = System.currentTimeMillis();
    Long last = lastAsk.get(id);
    if (last != null && now - last < 3000) return;
    lastAsk.put(id, now);

    if (!hasKey()) {
      onReply.accept("My brain is not connected yet.");
      player.sendMessage(Text.literal("§e[Cubix] API anahtari yok. Sunucuda config/korkumod-cube.properties dosyasina yaz, sonra /cube reload de."), false);
      return;
    }

    Deque<String[]> hist = history.computeIfAbsent(id, k -> new ArrayDeque<>());
    JsonArray contents = new JsonArray();
    for (String[] turn : hist) {
      contents.add(content(turn[0], turn[1]));
    }
    contents.add(content("user", userText));

    JsonObject sp = new JsonObject();
    sp.addProperty("text", SYSTEM_PROMPT + " The player's name is " + player.getName().getString() + ".");
    JsonArray sysParts = new JsonArray();
    sysParts.add(sp);
    JsonObject sys = new JsonObject();
    sys.add("parts", sysParts);

    JsonObject gen = new JsonObject();
    gen.addProperty("maxOutputTokens", 600);
    gen.addProperty("temperature", 1.0);

    JsonObject body = new JsonObject();
    body.add("systemInstruction", sys);
    body.add("contents", contents);
    body.add("generationConfig", gen);

    HttpRequest req = HttpRequest.newBuilder(
            URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
        .timeout(Duration.ofSeconds(25))
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();

    http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
      String reply;
      String hint = null;
      boolean ok = false;
      if (err != null) {
        reply = "My brain connection is broken. Try again.";
        hint = "Baglanti hatasi: " + err.getMessage();
      } else if (resp.statusCode() == 200) {
        String t = extractText(resp.body());
        if (t == null || t.isBlank()) {
          reply = "...";
        } else {
          reply = clean(t);
          ok = true;
        }
      } else if (resp.statusCode() == 429) {
        reply = "I am tired. Ask me again in a minute.";
      } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
        reply = "My brain key does not work.";
        hint = "API anahtari reddedildi (HTTP " + resp.statusCode() + "). Anahtari ve internet baglantisini kontrol et.";
      } else if (resp.statusCode() == 404) {
        reply = "I cannot find my brain.";
        hint = "Model bulunamadi: config dosyasinda model satirini degistir.";
      } else {
        reply = "Something broke inside my head.";
        hint = "HTTP " + resp.statusCode();
      }
      if (resp != null && resp.statusCode() != 200) {
        String b = resp.body();
        KorkuMod.LOGGER.warn("Gemini HTTP " + resp.statusCode() + ": " + (b.length() > 300 ? b.substring(0, 300) : b));
      }
      final String r = reply;
      final String h = hint;
      final boolean good = ok;
      server.execute(() -> {
        if (good) {
          hist.addLast(new String[]{"user", userText});
          hist.addLast(new String[]{"model", r});
          while (hist.size() > 12) {
            hist.removeFirst();
          }
        }
        if (h != null) {
          player.sendMessage(Text.literal("§c[Cubix] " + h), false);
        }
        onReply.accept(r);
      });
    });
  }

  private JsonObject content(String role, String text) {
    JsonObject part = new JsonObject();
    part.addProperty("text", text);
    JsonArray parts = new JsonArray();
    parts.add(part);
    JsonObject c = new JsonObject();
    c.addProperty("role", role);
    c.add("parts", parts);
    return c;
  }

  private String extractText(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      JsonArray cands = root.getAsJsonArray("candidates");
      if (cands == null || cands.size() == 0) return null;
      JsonObject content = cands.get(0).getAsJsonObject().getAsJsonObject("content");
      if (content == null) return null;
      JsonArray parts = content.getAsJsonArray("parts");
      if (parts == null) return null;
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parts.size(); i++) {
        JsonObject p = parts.get(i).getAsJsonObject();
        if (p.has("text") && !(p.has("thought") && p.get("thought").getAsBoolean())) {
          sb.append(p.get("text").getAsString());
        }
      }
      return sb.toString();
    } catch (Exception e) {
      return null;
    }
  }

  private String clean(String t) {
    String s = t.replace("*", "").replace("\n", " ").replace("\r", " ").trim();
    if (s.length() > 220) {
      s = s.substring(0, 217) + "...";
    }
    return s;
  }
      }
