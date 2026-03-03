package com.runelite.poisondynamite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
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

	private final OkHttpClient httpClient;
	private final Map<String, NpcDefenceStats> cache = new ConcurrentHashMap<>();
	// Track in-flight requests so we don't duplicate fetches
	private final Map<String, Boolean> pending = new ConcurrentHashMap<>();

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

		String key = npcName + ":" + npcId;
		NpcDefenceStats cached = cache.get(key);
		if (cached != null)
		{
			return cached;
		}

		// Also check by name only (for NPCs where we haven't matched ID yet)
		cached = cache.get(npcName);
		if (cached != null)
		{
			return cached;
		}

		// Start fetch if not already pending
		if (pending.putIfAbsent(key, Boolean.TRUE) == null)
		{
			fetchStats(npcName, npcId, key);
		}

		return null;
	}

	boolean isLoading(String npcName, int npcId)
	{
		if (npcName == null)
		{
			return false;
		}
		String key = npcName + ":" + npcId;
		return pending.containsKey(key) && !cache.containsKey(key);
	}

	private void fetchStats(String npcName, int npcId, String cacheKey)
	{
		String query = "bucket('infobox_monster')" +
			".select('id','defence_level','stab_defence_bonus','slash_defence_bonus'," +
			"'crush_defence_bonus','magic_defence_bonus','range_defence_bonus')" +
			".where('name','" + npcName.replace("'", "\\'") + "').run()";

		HttpUrl url = WIKI_API_URL.newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", query)
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", "poison-dynamite-runelite-plugin")
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to fetch NPC stats for {}", npcName, e);
				// Cache zeroed stats as fallback
				NpcDefenceStats fallback = new NpcDefenceStats(0, 0, 0, 0, 0, 0);
				cache.put(cacheKey, fallback);
				pending.remove(cacheKey);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.warn("Wiki API returned {} for {}", response.code(), npcName);
						cache.put(cacheKey, new NpcDefenceStats(0, 0, 0, 0, 0, 0));
						pending.remove(cacheKey);
						return;
					}

					String body = response.body().string();
					NpcDefenceStats stats = parseResponse(body, npcId);
					cache.put(cacheKey, stats);
					pending.remove(cacheKey);
					log.debug("Fetched NPC stats for {} (id={}): {}", npcName, npcId, stats);
				}
				catch (Exception e)
				{
					log.warn("Error parsing NPC stats for {}", npcName, e);
					cache.put(cacheKey, new NpcDefenceStats(0, 0, 0, 0, 0, 0));
					pending.remove(cacheKey);
				}
			}
		});
	}

	private NpcDefenceStats parseResponse(String json, int targetNpcId)
	{
		JsonObject root = new JsonParser().parse(json).getAsJsonObject();

		if (!root.has("result"))
		{
			return new NpcDefenceStats(0, 0, 0, 0, 0, 0);
		}

		JsonArray result = root.getAsJsonArray("result");
		if (result.size() == 0)
		{
			return new NpcDefenceStats(0, 0, 0, 0, 0, 0);
		}

		// Each result entry has arrays for each field; find the variant matching our NPC ID
		JsonObject entry = result.get(0).getAsJsonObject();

		JsonArray ids = getJsonArray(entry, "id");
		JsonArray defLevels = getJsonArray(entry, "defence_level");
		JsonArray stabDefs = getJsonArray(entry, "stab_defence_bonus");
		JsonArray slashDefs = getJsonArray(entry, "slash_defence_bonus");
		JsonArray crushDefs = getJsonArray(entry, "crush_defence_bonus");
		JsonArray magicDefs = getJsonArray(entry, "magic_defence_bonus");
		JsonArray rangeDefs = getJsonArray(entry, "range_defence_bonus");

		// Try to match by NPC ID
		int matchIndex = 0;
		if (ids != null)
		{
			for (int i = 0; i < ids.size(); i++)
			{
				JsonElement idElem = ids.get(i);
				if (!idElem.isJsonNull() && idElem.getAsInt() == targetNpcId)
				{
					matchIndex = i;
					break;
				}
			}
		}

		return new NpcDefenceStats(
			getIntAt(defLevels, matchIndex),
			getIntAt(stabDefs, matchIndex),
			getIntAt(slashDefs, matchIndex),
			getIntAt(crushDefs, matchIndex),
			getIntAt(magicDefs, matchIndex),
			getIntAt(rangeDefs, matchIndex)
		);
	}

	private static JsonArray getJsonArray(JsonObject obj, String key)
	{
		if (obj.has(key) && obj.get(key).isJsonArray())
		{
			return obj.getAsJsonArray(key);
		}
		return null;
	}

	private static int getIntAt(JsonArray array, int index)
	{
		if (array == null || index >= array.size())
		{
			return 0;
		}
		JsonElement elem = array.get(index);
		if (elem.isJsonNull())
		{
			return 0;
		}
		try
		{
			return elem.getAsInt();
		}
		catch (NumberFormatException e)
		{
			return 0;
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

		NpcDefenceStats(int defenceLevel, int stabDef, int slashDef, int crushDef,
			int magicDef, int rangeDef)
		{
			this.defenceLevel = defenceLevel;
			this.stabDef = stabDef;
			this.slashDef = slashDef;
			this.crushDef = crushDef;
			this.magicDef = magicDef;
			this.rangeDef = rangeDef;
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
