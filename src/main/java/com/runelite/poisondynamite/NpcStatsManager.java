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
			.header("User-Agent", "poison-dynamite-runelite-plugin")
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

					cache.put(cacheKey, stats);
					failures.remove(cacheKey);
					pending.remove(cacheKey);
					log.debug("Fetched NPC stats for {} (id={}): {}", npcName, npcId, stats);
				}
				catch (Exception e)
				{
					log.warn("Error parsing NPC stats for {}", npcName, e);
					fail(cacheKey);
				}
			}
		});
	}

	private void fail(String cacheKey)
	{
		failures.put(cacheKey, Instant.now());
		pending.remove(cacheKey);
	}

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
