package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 행복한 순간·좋아하는 것과 이은 줄이 실제 Room에서 남고 풀리는지 봅니다(사용자가 정함, 2026-11-12). 메모리 DB만 씁니다.
 *
 * 줄을 풀 때 표식이 없으면 다른 기기의 사본이 다음 병합에 줄을 되살립니다. 루틴을 합치면 줄도 남는 루틴을 따라가야 합니다.
 */
class HappyRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private val zone = ZoneId.of("Asia/Seoul")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao(),
            recordLinkDao = database.recordLinkDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aMomentIsKeptWithItsDayAndLinkedLikes() = runBlocking {
        val sunsetId = repository.saveLike(0, " 노을 ", "장소", "하루가 끝나는 색", emptyList())
        val walkId = repository.saveLike(0, "산책", "활동", "", emptyList())
        val sunset = database.contentItemDao().getById(sunsetId)!!
        val walk = database.contentItemDao().getById(walkId)!!
        val now = LocalDate.of(2026, 11, 12).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()

        val momentId = repository.saveHappyMoment(
            id = 0,
            text = "  퇴근길 노을이 너무 예뻤다\n하늘이 분홍색  ",
            date = LocalDate.of(2026, 11, 11),
            imageUris = listOf("content://photo/1", "content://photo/1"),
            likeSyncIds = listOf(sunset.syncId, walk.syncId),
            now = now,
            zoneId = zone
        )

        val moment = database.contentItemDao().getById(momentId)!!
        assertEquals(ContentType.HAPPY_MOMENT, moment.type)
        assertEquals("퇴근길 노을이 너무 예뻤다", moment.title)
        assertEquals(LocalDate.of(2026, 11, 11), Happy.dateOf(moment.createdAt, zone))
        assertEquals(listOf("content://photo/1"), moment.imageUris)
        assertEquals("노을", sunset.title)
        assertEquals(
            setOf(sunset.syncId, walk.syncId),
            RecordLinks.linkedTo(database.recordLinkDao().getAll(), RecordRef.of(moment)).map { it.syncId }.toSet()
        )
    }

    @Test
    fun droppingALikeChipUnlinksItWithATombstoneButKeepsOtherLinks() = runBlocking {
        val sunset = database.contentItemDao().getById(repository.saveLike(0, "노을", "", "", emptyList()))!!
        val momentId = repository.saveHappyMoment(0, "노을", LocalDate.now(), emptyList(), listOf(sunset.syncId))
        val moment = database.contentItemDao().getById(momentId)!!
        // 상세의 "기록 잇기"로 이은 일기 줄. 칩은 좋아하는 것만 다루므로 이 줄은 그대로여야 합니다.
        val diary = RecordRef(RecordKind.DIARY.name, "diary-1")
        repository.linkRecords(RecordRef.of(moment), diary)
        val likeLink = RecordLinks.between(database.recordLinkDao().getAll(), RecordRef.of(moment), RecordRef.of(sunset))!!

        repository.saveHappyMoment(momentId, "노을", LocalDate.now(), emptyList(), likeSyncIds = emptyList())

        val left = RecordLinks.linkedTo(database.recordLinkDao().getAll(), RecordRef.of(moment))
        assertEquals(listOf(diary), left)
        assertTrue(database.deletionDao().getAll().any { it.syncId == likeLink.syncId && it.entityType == SyncEntityType.RECORD_LINK })
    }

    @Test
    fun aLikeWithTheSameNameIsNotMadeTwice() = runBlocking {
        val first = repository.saveLike(0, "떡볶이", "음식", "", emptyList())
        val again = repository.saveLike(0, " 떡 볶이 ", "", "", emptyList())

        assertEquals(first, again)
        assertEquals(1, database.contentItemDao().getAll().count { it.type == ContentType.LIKE })
    }

    @Test
    fun linkingTwiceFromEitherSideKeepsOneLineAndUnlinkingLeavesATombstone() = runBlocking {
        val a = RecordRef(RecordKind.CONTENT_ITEM.name, "moment-1")
        val b = RecordRef(RecordKind.BOOK.name, "book-1")

        assertTrue(repository.linkRecords(a, b))
        assertFalse(repository.linkRecords(b, a))
        assertEquals(1, database.recordLinkDao().getAll().size)

        val link = database.recordLinkDao().getAll().single()
        assertEquals(1, repository.unlinkRecords(b, a))
        assertTrue(database.recordLinkDao().getAll().isEmpty())
        assertTrue(database.deletionDao().getAll().any { it.syncId == link.syncId && it.entityType == SyncEntityType.RECORD_LINK })
    }

    @Test
    fun mergingRoutinesMovesTheirLinksToTheKeptRoutine() = runBlocking {
        val keepId = database.routineDao().insert(RoutineEntity(title = "저녁 산책"))
        val goneId = database.routineDao().insert(RoutineEntity(title = "밤 산책"))
        val keep = database.routineDao().getById(keepId)!!
        val gone = database.routineDao().getById(goneId)!!
        val moment = RecordRef(RecordKind.CONTENT_ITEM.name, "moment-1")
        val other = RecordRef(RecordKind.CONTENT_ITEM.name, "moment-2")
        repository.linkRecords(moment, RecordRef.of(gone))
        // 둘 다와 이어진 순간은 합친 뒤 한 줄만 남습니다.
        repository.linkRecords(other, RecordRef.of(gone))
        repository.linkRecords(other, RecordRef.of(keep))

        repository.mergeRoutines(keepId, goneId, "저녁 산책", "")

        val links = database.recordLinkDao().getAll()
        assertEquals(listOf(RecordRef.of(keep)), RecordLinks.linkedTo(links, moment))
        assertEquals(listOf(RecordRef.of(keep)), RecordLinks.linkedTo(links, other))
        assertEquals(2, links.size)
        assertTrue(links.none { it.fromSyncId == gone.syncId || it.toSyncId == gone.syncId })
    }
}
