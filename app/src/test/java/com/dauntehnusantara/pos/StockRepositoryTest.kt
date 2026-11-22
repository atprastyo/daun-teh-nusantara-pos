package com.dauntehnusantara.pos

import android.app.Application
import androidx.room.Room
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.stock.domain.*
import com.dauntehnusantara.pos.feature.stock.data.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class StockRepositoryTest {
    private lateinit var db:PosDatabase
    private val scope=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val uuid="00000000-0000-4000-8000-000000000001"
    private val command=StockCommand(uuid,uuid,uuid,12,0,"Opening count")
    private val snapshot=StockSnapshot(uuid,1800000000000,listOf(StockProduct(uuid,"Tea",true,null,0)),false,emptyList())
    private var current:PullScope?=scope
    private var offline=false
    private var failure:String?=null
    private var calls=0
    private val seen=mutableSetOf<String>()
    private val access=object:ReportingGateway {
        override suspend fun reportingScope(online:Boolean)=current.takeUnless{online&&offline}
        override suspend fun report(query:ReportQuery):SalesReport=error("unused")
    }
    private val remote=object:StockGateway {
        override suspend fun stock(scope:PullScope,query:StockQuery):StockSnapshot {failure?.let{throw ManagementFailure(it)};return snapshot}
        override suspend fun setStock(scope:PullScope,command:StockCommand){calls++;seen.add(command.operationId);failure?.let{throw ManagementFailure(it)}}
    }
    private fun open(){db=Room.databaseBuilder(RuntimeEnvironment.getApplication(),PosDatabase::class.java,"stock-test.db").addCallback(CatalogConstraints).build()}
    @Before fun before(){RuntimeEnvironment.getApplication().deleteDatabase("stock-test.db");open()}
    @After fun after(){db.close();RuntimeEnvironment.getApplication().deleteDatabase("stock-test.db")}
    @Test fun lostAckAndReopenRetainFrozenOperationUntilRetryConfirms():Unit=runBlocking {
        var repo=RoomStockRepository(db,access,remote);repo.prepare(scope,command);failure="UNAVAILABLE"
        try{repo.submit(scope);fail("Expected uncertainty")}catch(_:ManagementFailure){}
        db.close();open();repo=RoomStockRepository(db,access,remote)
        assertEquals(command,repo.pending(scope));failure=null;repo.submit(scope)
        assertNull(repo.pending(scope));assertEquals(2,calls);assertEquals(1,seen.size)
    }
    @Test fun pendingCannotBeReplacedAndConflictRequiresExplicitNewCount():Unit=runBlocking {
        val repo=RoomStockRepository(db,access,remote);repo.prepare(scope,command)
        try{repo.prepare(scope,command.copy(quantity=13));fail("Cannot overwrite pending")}catch(_:ManagementFailure){}
        assertEquals(command,repo.pending(scope));failure="CONFLICT"
        try{repo.submit(scope);fail("Expected conflict")}catch(_:ManagementFailure){}
        assertNull(repo.pending(scope))
    }
    @Test fun cacheSurvivesReopenAndIsExactQueryAccountRoleAndProjectScoped():Unit=runBlocking {
        var repo=RoomStockRepository(db,access,remote);val q=StockQuery();repo.refresh(scope,q)
        db.close();open();repo=RoomStockRepository(db,access,remote);offline=true
        assertEquals(snapshot,repo.cached(scope,q));assertNull(repo.cached(scope,q.copy(search="other")))
        try{repo.prepare(scope,command);fail("No offline writes")}catch(_:ManagementFailure){}
        current=scope.copy(sale=scope.sale.copy(userId="other"))
        try{repo.cached(scope,q);fail("Old account denied")}catch(_:ManagementFailure){}
        assertNull(repo.cached(current!!,q));current=scope.copy(role="MANAGER");assertNull(repo.cached(current!!,q))
        current=scope.copy(project="https://other.invalid");assertNull(repo.cached(current!!,q))
    }
    @Test fun invalidInputAndUntrackedZeroRemainDistinct() {
        assertEquals(0L,StockRules.parse("0"));assertEquals(1000000000L,StockRules.parse("1000000000"))
        for(v in listOf("-1","1.5","1,5","1.000","1000000001","999999999999999999999",""))assertNull(v,StockRules.parse(v))
        assertEquals(snapshot,StockJson.read(StockJson.write(snapshot)))
        assertEquals(0L,StockJson.read(StockJson.write(snapshot.copy(products=listOf(snapshot.products[0].copy(quantity=0,revision=1))))).products[0].quantity)
    }
    @Test fun recoveryExportIncludesValidatedPendingCountWithoutCredentials():Unit=runBlocking {
        val repo=RoomStockRepository(db,access,remote);repo.prepare(scope,command)
        val sale=object:SaleGateway {
            override suspend fun backgroundScope()=scope.sale
            override suspend fun checkoutScope()=scope.sale
            override suspend fun submit(sale:PendingSale):PushResult=error("unused")
        }
        val pull=object:PullGateway {
            override suspend fun pullScope()=scope
            override suspend fun fetch(scope:PullScope,after:Long,epoch:String?):PullPage=error("unused")
        }
        val recovery=com.dauntehnusantara.pos.feature.recovery.data.RoomRecoveryRepository(db,sale,pull,java.time.Clock.systemUTC())
        val raw=recovery.capture(scope);val payload=org.json.JSONObject(raw).getJSONObject("payload")
        assertEquals(command,StockJson.command(payload.getString("stock_pending")))
        assertTrue(com.dauntehnusantara.pos.feature.recovery.data.RecoveryArchive.inspect(raw,scope).isEmpty())
        assertFalse(raw.contains("device_secret"));assertFalse(raw.contains("access_token"))
        payload.put("stock_pending",StockJson.command(command.copy(quantity=-1)))
        try {
            com.dauntehnusantara.pos.feature.recovery.data.RecoveryArchive.inspect(com.dauntehnusantara.pos.feature.recovery.data.RecoveryArchive.encode(payload),scope)
            fail("Invalid pending command must be rejected even with a matching checksum")
        }catch(_:IllegalArgumentException){}
    }

}
