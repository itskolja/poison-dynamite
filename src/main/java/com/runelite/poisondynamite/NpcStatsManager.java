package com.runelite.poisondynamite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
class NpcStatsManager
{
	private static final HttpUrl WIKI_API_URL = HttpUrl.parse(
		"https://oldschool.runescape.wiki/api.php");
	private static final String USER_AGENT =
		"poison-dynamite-runelite-plugin (github.com/yonnski/poison-dynamite)";
	private static final long RETRY_BACKOFF_MILLIS = 30_000;

	private final OkHttpClient httpClient;
	private final Map<String, NpcDefenceStats> cache = new ConcurrentHashMap<>();
	// Track in-flight requests so we don't duplicate fetches
	private final Map<String, Boolean> pending = new ConcurrentHashMap<>();
	// Failed lookups are retried after a backoff instead of being cached as zeros
	private final Map<String, Instant> failures = new ConcurrentHashMap<>();

	@Inject
	NpcStatsManager(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	NpcDefenceStats getStats(String npcName, int npcId)
	{
		if (npcName == null)
		{
			return null;
		}

		String key = key(npcName, npcId);
		NpcDefenceStats cached = cache.get(key);
		if (cached != null)
		{
			return cached;
		}

		Instant failedAt = failures.get(key);
		if (failedAt != null
			&& Instant.now().isBefore(failedAt.plusMillis(RETRY_BACKOFF_MILLIS)))
		{
			return null;
		}

		// Start fetch if not already pending
		if (pending.putIfAbsent(key, Boolean.TRUE) == null)
		{
			fetchStats(npcName, npcId, key, false);
		}

		return null;
	}

	boolean isUnavailable(String npcName, int npcId)
	{
		return npcName != null && failures.containsKey(key(npcName, npcId));
	}

	private static String key(String npcName, int npcId)
	{
		return npcName + ":" + npcId;
	}

	private void fetchStats(String npcName, int npcId, String cacheKey, boolean byId)
	{
		String filter = byId
			? ".where('id'," + npcId + ")"
			: ".where('name','" + npcName.replace("'", "\\'") + "')";
		String query = "bucket('infobox_monster')" +
			".select('id','defence_level','stab_defence_bonus','slash_defence_bonus'," +
			"'crush_defence_bonus','magic_defence_bonus','range_defence_bonus')" +
			filter + ".run()";

		HttpUrl url = WIKI_API_URL.newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", query)
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to fetch NPC stats for {}", npcName, e);
				fail(cacheKey);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.warn("Wiki API returned {} for {}", response.code(), npcName);
						fail(cacheKey);
						return;
					}

					String body = response.body().string();
					NpcDefenceStats stats = parseResponse(body, npcId);
					if (stats == null)
					{
						if (!byId)
						{
							// in-game name may not match the wiki page name;
							// retry the lookup by NPC id
							log.debug("No wiki match by name for {}, retrying by id {}", npcName, npcId);
							fetchStats(npcName, npcId, cacheKey, true);
							return;
						}
						log.warn("No wiki stats found for {} (id={})", npcName, npcId);
						fail(cacheKey);
						return;
					}

					// defence stats are in hand; poison resistance comes from the
					// page wikitext (the bucket's poison_resistance field is broken)
					fetchPoisonResistance(npcName, npcId, cacheKey, stats);
				}
				catch (Exception e)
				{
					log.warn("Error parsing NPC stats for {}", npcName, e);
					fail(cacheKey);
				}
			}
		});
	}

	private void fetchPoisonResistance(String npcName, int npcId, String cacheKey,
		NpcDefenceStats stats)
	{
		HttpUrl url = WIKI_API_URL.newBuilder()
			.addQueryParameter("action", "parse")
			.addQueryParameter("page", npcName)
			.addQueryParameter("prop", "wikitext")
			.addQueryParameter("redirects", "1")
			.addQueryParameter("format", "json")
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to fetch poison resistance for {}", npcName, e);
				succeed(cacheKey, stats);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				String resistance = null;
				try (response)
				{
					if (response.isSuccessful())
					{
						resistance = parsePoisonResistance(response.body().string(), npcId);
					}
				}
				catch (Exception e)
				{
					log.warn("Error parsing poison resistance for {}", npcName, e);
				}
				succeed(cacheKey, stats.withPoisonResistance(resistance));
				log.debug("Poison resistance for {} (id={}): {}", npcName, npcId, resistance);
			}
		});
	}

	private void succeed(String cacheKey, NpcDefenceStats stats)
	{
		cache.put(cacheKey, stats);
		failures.remove(cacheKey);
		pending.remove(cacheKey);
	}

	private void fail(String cacheKey)
	{
		failures.put(cacheKey, Instant.now());
		pending.remove(cacheKey);
	}

	// Reads |poisonresistance (or |poisonresistanceN for versioned infoboxes)
	// from the page wikitext, matching the version whose |idN list contains
	// the target NPC id, falling back to the first value on the page.
	static String parsePoisonResistance(String parseApiJson, int targetNpcId)
	{
		JsonObject root = new JsonParser().parse(parseApiJson).getAsJsonObject();
		if (!root.has("parse"))
		{
			return null;
		}
		JsonObject parse = root.getAsJsonObject("parse");
		if (!parse.has("wikitext"))
		{
			return null;
		}
		String wikitext = parse.getAsJsonObject("wikitext").get("*").getAsString();

		Map<String, String> resistanceBySuffix = new java.util.HashMap<>();
		String matchedSuffix = null;
		String firstSuffix = null;

		java.util.regex.Matcher m = INFOBOX_PARAM_PATTERN.matcher(wikitext);
		while (m.find())
		{
			String param = m.group(1);
			String suffix = m.group(2);
			String value = m.group(3).trim();

			if (param.equals("poisonresistance"))
			{
				resistanceBySuffix.putIfAbsent(suffix, value);
				if (firstSuffix == null)
				{
					firstSuffix = suffix;
				}
			}
			else if (matchedSuffix == null) // id param
			{
				for (String idStr : value.split(","))
				{
					try
					{
						if (Integer.parseInt(idStr.trim()) == targetNpcId)
						{
							matchedSuffix = suffix;
							break;
						}
					}
					catch (NumberFormatException ignored)
					{
					}
				}
			}
		}

		String value = matchedSuffix != null ? resistanceBySuffix.get(matchedSuffix) : null;
		if (value == null && firstSuffix != null)
		{
			value = resistanceBySuffix.get(firstSuffix);
		}
		if (value == null || value.isEmpty())
		{
			return null;
		}
		return value.toLowerCase();
	}

	private static final java.util.regex.Pattern INFOBOX_PARAM_PATTERN =
		java.util.regex.Pattern.compile("\\|\\s*(id|poisonresistance)(\\d*)\\s*=\\s*([^\\n|]*)");

	// The bucket API returns one row per infobox version: scalar stat fields
	// plus an "id" array listing the NPC ids that version covers.
	static NpcDefenceStats parseResponse(String json, int targetNpcId)
	{
		JsonObject root = new JsonParser().parse(json).getAsJsonObject();
		if (!root.has("bucket") || !root.get("bucket").isJsonArray())
		{
			return null;
		}

		JsonArray rows = root.getAsJsonArray("bucket");
		if (rows.size() == 0)
		{
			return null;
		}

		JsonObject match = null;
		for (JsonElement rowElem : rows)
		{
			if (rowElem.isJsonObject() && rowContainsId(rowElem.getAsJsonObject(), targetNpcId))
			{
				match = rowElem.getAsJsonObject();
				break;
			}
		}
		if (match == null)
		{
			match = rows.get(0).getAsJsonObject();
		}

		return new NpcDefenceStats(
			getInt(match, "defence_level"),
			getInt(match, "stab_defence_bonus"),
			getInt(match, "slash_defence_bonus"),
			getInt(match, "crush_defence_bonus"),
			getInt(match, "magic_defence_bonus"),
			getInt(match, "range_defence_bonus")
		);
	}

	private static boolean rowContainsId(JsonObject row, int targetNpcId)
	{
		if (!row.has("id"))
		{
			return false;
		}
		JsonElement idElem = row.get("id");
		if (idElem.isJsonArray())
		{
			for (JsonElement e : idElem.getAsJsonArray())
			{
				if (asInt(e, -1) == targetNpcId)
				{
					return true;
				}
			}
			return false;
		}
		return asInt(idElem, -1) == targetNpcId;
	}

	private static int getInt(JsonObject row, String key)
	{
		if (!row.has(key))
		{
			return 0;
		}
		JsonElement elem = row.get(key);
		if (elem.isJsonArray())
		{
			JsonArray array = elem.getAsJsonArray();
			elem = array.size() > 0 ? array.get(0) : null;
		}
		return elem == null ? 0 : asInt(elem, 0);
	}

	private static int asInt(JsonElement elem, int fallback)
	{
		if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive())
		{
			return fallback;
		}
		try
		{
			return elem.getAsInt();
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	static class NpcDefenceStats
	{
		final int defenceLevel;
		final int stabDef;
		final int slashDef;
		final int crushDef;
		final int magicDef;
		final int rangeDef;
		// "0", "100", "200" or "poison" (immune); null when unknown
		final String poisonResistance;

		NpcDefenceStats(int defenceLevel, int stabDef, int slashDef, int crushDef,
			int magicDef, int rangeDef)
		{
			this(defenceLevel, stabDef, slashDef, crushDef, magicDef, rangeDef, null);
		}

		NpcDefenceStats(int defenceLevel, int stabDef, int slashDef, int crushDef,
			int magicDef, int rangeDef, String poisonResistance)
		{
			this.defenceLevel = defenceLevel;
			this.stabDef = stabDef;
			this.slashDef = slashDef;
			this.crushDef = crushDef;
			this.magicDef = magicDef;
			this.rangeDef = rangeDef;
			this.poisonResistance = poisonResistance;
		}

		NpcDefenceStats withPoisonResistance(String resistance)
		{
			return new NpcDefenceStats(defenceLevel, stabDef, slashDef, crushDef,
				magicDef, rangeDef, resistance);
		}

		// resistance 100 blocks poison, 200 blocks poison and venom, and
		// "poison" marks poison-based monsters — only 0 can be poisoned
		boolean isPoisonImmune()
		{
			return poisonResistance != null && !"0".equals(poisonResistance);
		}

		int getDefenceForStyle(String style)
		{
			if (style == null)
			{
				return stabDef;
			}
			switch (style.toLowerCase())
			{
				case "slash":
					return slashDef;
				case "crush":
					return crushDef;
				case "magic":
					return magicDef;
				case "ranged":
				case "range":
					return rangeDef;
				case "stab":
				default:
					return stabDef;
			}
		}

		@Override
		public String toString()
		{
			return "NpcDefenceStats{def=" + defenceLevel +
				", stab=" + stabDef + ", slash=" + slashDef +
				", crush=" + crushDef + ", magic=" + magicDef +
				", range=" + rangeDef + "}";
		}
	}
}
