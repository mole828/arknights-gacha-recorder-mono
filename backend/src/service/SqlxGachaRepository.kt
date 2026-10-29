package com.example.service

import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.impl.extensions.asBoolean
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asLong
import io.github.smyrgeorge.sqlx4k.impl.extensions.asUInt
import io.github.smyrgeorge.sqlx4k.impl.extensions.asULong
import io.github.smyrgeorge.sqlx4k.postgres.IPostgresSQL
import io.github.smyrgeorge.sqlx4k.postgres.postgreSQL
import model.AccountInfo
import model.Char
import model.Draw
import model.GachaPageRequest
import model.GachaRecord
import model.GachaRepository
import model.GachasResponse
import model.GachasResponseData
import model.HgToken
import model.Pagination
import model.Uid
import model.UserToken

class SqlxGachaRepository(private val db: IPostgresSQL) : GachaRepository {
    suspend fun initSchema() {
        db.execute(
            """
            create table if not exists "user" (
                uid text primary key,
                nick_name text not null,
                hg_token text not null,
                expired boolean not null default false,
                channel_master_id integer not null default 1,
                channel_name text not null default '官服',
                is_default boolean not null default false,
                is_deleted boolean not null default false,
                is_official boolean not null default true
            );
            """.trimIndent()
        ).getOrThrow()

        db.execute(
            """
            create table if not exists gacha (
                uid text not null references "user"(uid),
                gacha_ts numeric(20, 0) not null,
                pos integer not null,
                char_id text not null,
                char_name text not null,
                pool_id text not null,
                pool_name text not null,
                rarity integer not null,
                is_new boolean not null,
                primary key (uid, gacha_ts, pos)
            );
            """.trimIndent()
        ).getOrThrow()

        db.execute("""create index if not exists idx_gacha_uid_ts_pos on gacha(uid, gacha_ts desc, pos desc);""")
            .getOrThrow()
        db.execute("""create index if not exists idx_user_expired on "user"(expired);""").getOrThrow()
    }

    override suspend fun upsertUser(accountInfo: AccountInfo, hgToken: HgToken) {
        db.execute(
            Statement.create(
                """
                insert into "user" (
                    uid, nick_name, hg_token, expired, channel_master_id, channel_name,
                    is_default, is_deleted, is_official
                ) values (
                    :uid, :nick_name, :hg_token, false, :channel_master_id, :channel_name,
                    :is_default, :is_deleted, :is_official
                )
                on conflict (uid) do update set
                    nick_name = excluded.nick_name,
                    hg_token = excluded.hg_token,
                    expired = false,
                    channel_master_id = excluded.channel_master_id,
                    channel_name = excluded.channel_name,
                    is_default = excluded.is_default,
                    is_deleted = excluded.is_deleted,
                    is_official = excluded.is_official;
                """.trimIndent()
            ).bindAccount(accountInfo).bind("hg_token", hgToken.content)
        ).getOrThrow()
    }

    override suspend fun updateUser(accountInfo: AccountInfo) {
        db.execute(
            Statement.create(
                """
                update "user" set
                    nick_name = :nick_name,
                    channel_master_id = :channel_master_id,
                    channel_name = :channel_name,
                    is_default = :is_default,
                    is_deleted = :is_deleted,
                    is_official = :is_official
                where uid = :uid;
                """.trimIndent()
            ).bindAccount(accountInfo)
        ).getOrThrow()
    }

    override suspend fun expireUid(uid: Uid) {
        db.execute(Statement.create("""update "user" set expired = true where uid = :uid;""").bind("uid", uid))
            .getOrThrow()
    }

    override suspend fun expireToken(hgToken: HgToken) {
        setTokenExpired(hgToken, true)
    }

    override suspend fun setTokenExpired(hgToken: HgToken, expired: Boolean) {
        db.execute(
            Statement.create("""update "user" set expired = :expired where hg_token = :hg_token;""")
                .bind("expired", expired)
                .bind("hg_token", hgToken.content)
        ).getOrThrow()
    }

    override suspend fun activeTokens(): List<UserToken> =
        db.fetchAll(
            """
            select uid, hg_token, nick_name
            from "user"
            where expired = false;
            """.trimIndent()
        ).getOrThrow().map { row ->
            UserToken(
                uid = row.get("uid").asString(),
                hgToken = HgToken(row.get("hg_token").asString()),
                nickName = row.get("nick_name").asString(),
            )
        }

    override suspend fun users(): List<AccountInfo> =
        db.fetchAll("""select * from "user" order by nick_name;""").getOrThrow().map { it.toAccountInfo() }

    override suspend fun expiredUsers(): List<AccountInfo> =
        db.fetchAll("""select * from "user" where expired = true order by nick_name;""")
            .getOrThrow()
            .map { it.toAccountInfo() }

    override suspend fun gachaPage(uid: Uid?, page: Long, pageSize: Int): GachasResponse {
        val offset = GachaPageRequest(page, pageSize).offset
        val whereSql = if (uid == null) "" else "where g.uid = :uid"

        fun Statement.bindUidIfNeeded(): Statement =
            if (uid == null) this else bind("uid", uid)

        val total = db.fetchAll(
            Statement.create(
                """
                select count(*) as total
                from gacha g
                left join "user" u on u.uid = g.uid
                $whereSql;
                """.trimIndent()
            ).bindUidIfNeeded()
        ).getOrThrow().first().get("total").asLong()

        val draws = db.fetchAll(
            Statement.create(
                """
                select
                    g.uid,
                    g.gacha_ts,
                    g.char_name,
                    g.pool_name,
                    g.rarity,
                    g.is_new,
                    coalesce(u.nick_name, '') as nick_name
                from gacha g
                left join "user" u on u.uid = g.uid
                $whereSql
                order by g.gacha_ts desc, g.pos desc
                limit :limit offset :offset;
                """.trimIndent()
            ).bindUidIfNeeded()
                .bind("limit", pageSize)
                .bind("offset", offset)
        ).getOrThrow().map { row ->
            Draw(
                chars = listOf(
                    Char(
                        isNew = row.get("is_new").asBoolean(),
                        rarity = row.get("rarity").asUInt(),
                        name = row.get("char_name").asString(),
                    )
                ),
                nickName = row.get("nick_name").asString(),
                pool = row.get("pool_name").asString(),
                ts = row.get("gacha_ts").asULong() / 1000u,
                uid = row.get("uid").asString(),
            )
        }

        return GachasResponse(
            data = GachasResponseData(
                list = draws,
                pagination = Pagination(
                    current = page.toULong(),
                    pageSize = pageSize,
                    total = total.toULong(),
                )
            )
        )
    }

    override suspend fun record(gacha: GachaRecord): UInt {
        val affected = db.execute(
            Statement.create(
                """
                insert into gacha (
                    uid, gacha_ts, pos, char_id, char_name, pool_id, pool_name, rarity, is_new
                ) values (
                    :uid, :gacha_ts, :pos, :char_id, :char_name, :pool_id, :pool_name, :rarity, :is_new
                )
                on conflict (uid, gacha_ts, pos) do nothing;
                """.trimIndent()
            )
                .bind("uid", gacha.uid)
                .bind("gacha_ts", gacha.gachaTs.toLong())
                .bind("pos", gacha.pos.toInt())
                .bind("char_id", gacha.charId)
                .bind("char_name", gacha.charName)
                .bind("pool_id", gacha.poolId)
                .bind("pool_name", gacha.poolName)
                .bind("rarity", gacha.rarity.toInt())
                .bind("is_new", gacha.isNew)
        ).getOrThrow()
        return affected.toUInt()
    }

    private fun Statement.bindAccount(accountInfo: AccountInfo): Statement =
        bind("uid", accountInfo.uid)
            .bind("nick_name", accountInfo.nickName)
            .bind("channel_master_id", accountInfo.channelMasterId)
            .bind("channel_name", accountInfo.channelName)
            .bind("is_default", accountInfo.isDefault)
            .bind("is_deleted", accountInfo.isDeleted)
            .bind("is_official", accountInfo.isOfficial)

    private fun ResultSet.Row.toAccountInfo(): AccountInfo =
        AccountInfo(
            uid = get("uid").asString(),
            nickName = get("nick_name").asString(),
            channelMasterId = get("channel_master_id").asInt(),
            channelName = get("channel_name").asString(),
            isDefault = get("is_default").asBoolean(),
            isDeleted = get("is_deleted").asBoolean(),
            isOfficial = get("is_official").asBoolean(),
        )
}

fun createGachaRepository(
    url: String,
    username: String,
    password: String,
): SqlxGachaRepository {
    val normalizedUrl = url
        .removePrefix("jdbc:")
        .replaceFirst("postgres://", "postgresql://")

    return SqlxGachaRepository(
        db = postgreSQL(
            url = normalizedUrl,
            username = username,
            password = password,
        )
    )
}

private fun ResultSet.first(): ResultSet.Row {
    require(rows.isNotEmpty()) { "expected at least one row" }
    return rows.first()
}
