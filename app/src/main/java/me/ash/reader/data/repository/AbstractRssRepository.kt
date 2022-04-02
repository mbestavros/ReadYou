package me.ash.reader.data.repository

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.paging.PagingSource
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import me.ash.reader.currentAccountId
import me.ash.reader.data.account.AccountDao
import me.ash.reader.data.article.Article
import me.ash.reader.data.article.ArticleDao
import me.ash.reader.data.article.ArticleWithFeed
import me.ash.reader.data.article.ImportantCount
import me.ash.reader.data.feed.Feed
import me.ash.reader.data.feed.FeedDao
import me.ash.reader.data.group.Group
import me.ash.reader.data.group.GroupDao
import me.ash.reader.data.group.GroupWithFeed
import me.ash.reader.data.source.RssNetworkDataSource
import java.util.concurrent.TimeUnit

abstract class AbstractRssRepository constructor(
    private val context: Context,
    private val accountDao: AccountDao,
    private val articleDao: ArticleDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val rssNetworkDataSource: RssNetworkDataSource,
    private val workManager: WorkManager,
    private val dispatcherIO: CoroutineDispatcher,
) {
    data class SyncState(
        val feedCount: Int = 0,
        val syncedCount: Int = 0,
        val currentFeedName: String = "",
    ) {
        val isSyncing: Boolean = feedCount != 0 || syncedCount != 0 || currentFeedName != ""
        val isNotSyncing: Boolean = !isSyncing
    }

    abstract suspend fun updateArticleInfo(article: Article)

    abstract suspend fun subscribe(feed: Feed, articles: List<Article>)

    abstract suspend fun addGroup(name: String): String

    abstract suspend fun sync()

    fun doSync() {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            SyncWorker.repeatingRequest
        )
    }

    fun pullGroups(): Flow<MutableList<Group>> {
        return groupDao.queryAllGroup(context.currentAccountId).flowOn(dispatcherIO)
    }

    fun pullFeeds(): Flow<MutableList<GroupWithFeed>> {
        return groupDao.queryAllGroupWithFeedAsFlow(context.currentAccountId).flowOn(dispatcherIO)
    }

    fun pullArticles(
        groupId: String? = null,
        feedId: String? = null,
        isStarred: Boolean = false,
        isUnread: Boolean = false,
    ): PagingSource<Int, ArticleWithFeed> {
        val accountId = context.currentAccountId
        Log.i(
            "RLog",
            "pullArticles: accountId: ${accountId}, groupId: ${groupId}, feedId: ${feedId}, isStarred: ${isStarred}, isUnread: ${isUnread}"
        )
        return when {
            groupId != null -> when {
                isStarred -> articleDao
                    .queryArticleWithFeedByGroupIdWhenIsStarred(accountId, groupId, isStarred)
                isUnread -> articleDao
                    .queryArticleWithFeedByGroupIdWhenIsUnread(accountId, groupId, isUnread)
                else -> articleDao.queryArticleWithFeedByGroupIdWhenIsAll(accountId, groupId)
            }
            feedId != null -> when {
                isStarred -> articleDao
                    .queryArticleWithFeedByFeedIdWhenIsStarred(accountId, feedId, isStarred)
                isUnread -> articleDao
                    .queryArticleWithFeedByFeedIdWhenIsUnread(accountId, feedId, isUnread)
                else -> articleDao.queryArticleWithFeedByFeedIdWhenIsAll(accountId, feedId)
            }
            else -> when {
                isStarred -> articleDao
                    .queryArticleWithFeedWhenIsStarred(accountId, isStarred)
                isUnread -> articleDao
                    .queryArticleWithFeedWhenIsUnread(accountId, isUnread)
                else -> articleDao.queryArticleWithFeedWhenIsAll(accountId)
            }
        }
    }

    fun pullImportant(
        isStarred: Boolean = false,
        isUnread: Boolean = false,
    ): Flow<List<ImportantCount>> {
        val accountId = context.currentAccountId
        Log.i(
            "RLog",
            "pullImportant: accountId: ${accountId}, isStarred: ${isStarred}, isUnread: ${isUnread}"
        )
        return when {
            isStarred -> articleDao
                .queryImportantCountWhenIsStarred(accountId, isStarred)
            isUnread -> articleDao
                .queryImportantCountWhenIsUnread(accountId, isUnread)
            else -> articleDao.queryImportantCountWhenIsAll(accountId)
        }.flowOn(dispatcherIO)
    }

    suspend fun findFeedById(id: String): Feed? {
        return feedDao.queryById(id)
    }

    suspend fun findArticleById(id: String): ArticleWithFeed? {
        return articleDao.queryById(id)
    }

    suspend fun isFeedExist(url: String): Boolean {
        return feedDao.queryByLink(context.currentAccountId, url).isNotEmpty()
    }

    fun peekWork(): String {
        return workManager.getWorkInfosByTag("sync").get().size.toString()
    }

    suspend fun updateGroup(group: Group) {
        groupDao.update(group)
    }

    suspend fun updateFeed(feed: Feed) {
        feedDao.update(feed)
    }

    suspend fun deleteGroup(group: Group) {
        groupDao.update(group)
    }

    suspend fun deleteFeed(feed: Feed) {
        articleDao.deleteByFeedId(context.currentAccountId, feed.id)
        feedDao.delete(feed)
    }

    companion object {
        val mutex = Mutex()

        private val _syncState = MutableStateFlow(SyncState())
        val syncState = _syncState.asStateFlow()

        fun updateSyncState(function: (SyncState) -> SyncState) {
            _syncState.update(function)
        }
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val rssRepository: RssRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("RLog", "doWork: ")
        rssRepository.get().sync()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "article.sync"

        val repeatingRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .build()
        ).addTag(WORK_NAME).build()
    }
}