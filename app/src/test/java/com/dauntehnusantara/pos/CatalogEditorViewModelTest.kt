package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.photos.domain.*
import com.dauntehnusantara.pos.feature.catalog.presentation.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class CatalogEditorViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){store.clear();Dispatchers.resetMain()}
    private val membership=Membership(DEMO_BRAND_ID,"Daun Teh","outlet-a","Outlet A",Role.OWNER)
    private val epoch="11111111-1111-4111-8111-111111111111"
    private val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea",version=1))
    private val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Original",version=2),category.info.id,6000,"existing",3)
    private val catalog=MutableStateFlow(Catalog(categories=listOf(category),products=listOf(product)))
    private val pullState=MutableStateFlow(PullStatus(1,true,100,null,epoch))
    private var mode="SUCCESS"
    private var photoMode="SUCCESS"
    private var pause:CompletableDeferred<Unit>?=null
    private var ignoreCancellation=false
    private val sent=mutableListOf<CatalogMutation>()
    private val photoSent=mutableListOf<PhotoCommand>()
    private var schedulerCalls=0
    private var prepared=0
    private val auth=object:AuthRepository {
        override val state=MutableStateFlow(AuthState(AuthStage.READY,true,membership=membership,email="one@example.invalid"))
        override suspend fun restore(){};override suspend fun signIn(email:String,password:String){};override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){};override suspend fun refresh(){};override suspend fun unlockOffline(){};override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){};override suspend fun approveDevice(id:String){};override suspend fun signOut(){}
    }
    private fun scope():PullScope?=auth.state.value.membership?.let{PullScope(SaleScope(it.brandId,it.outletId,"device",auth.state.value.email),it.role.name,"https://example.invalid")}
    private fun model():CatalogEditorViewModel {
        val repo=object:CatalogRepository {
            override fun observe(brandId:String)=catalog
            override suspend fun save(category:Category)=error("unused");override suspend fun save(product:Product)=error("unused")
            override suspend fun save(group:ModifierGroup)=error("unused");override suspend fun save(option:ModifierOption)=error("unused")
            override suspend fun assignGroups(brandId:String,productId:String,groupIds:List<String>,expectedVersion:Long){}
            override suspend fun archive(brandId:String,kind:CatalogRecord,id:String,expectedVersion:Long){}
            override suspend fun seedDemo(){}
        }
        return ViewModelProvider(store,object:ViewModelProvider.Factory{
            override fun<T:ViewModel>create(modelClass:Class<T>):T {
                @Suppress("UNCHECKED_CAST")
                return CatalogEditorViewModel(auth,object:ReportingGateway{
                    override suspend fun reportingScope(online:Boolean)=scope()
                    override suspend fun report(query:ReportQuery):SalesReport=error("unused")
                },ObserveCatalog(repo),object:PullRepository{
                    override fun observe(scope:PullScope)=pullState
                    override suspend fun sync()=false
                },EditCatalog(object:CatalogGateway{
                    override suspend fun saveCatalog(scope:PullScope,epoch:String,change:CatalogMutation):CatalogAck{
                        sent+=change;if(ignoreCancellation)withContext(NonCancellable){pause?.await()}else pause?.await();if(mode!="SUCCESS")throw ManagementFailure(mode)
                        return CatalogAck(change.operationId,change.id,change.expectedVersion+1,3,epoch)
                    }
                }),object:SyncScheduler{override fun enqueue(force:Boolean){schedulerCalls++};override fun ensurePeriodic(){}},
                object:ProductPhotoRepository{
                    override suspend fun load(path:String):ByteArray?=null
                    override suspend fun currentScope()=scope()
                    override suspend fun upload(scope:PullScope,path:String,bytes:ByteArray){}
                },object:ProductPhotoGateway{
                    override suspend fun photoScope()=scope()
                    override suspend fun uploadPhoto(scope:PullScope,path:String,bytes:ByteArray){}
                    override suspend fun downloadPhoto(scope:PullScope,path:String)=byteArrayOf()
                    override suspend fun setPhoto(scope:PullScope,epoch:String,command:PhotoCommand):PhotoAck{
                        photoSent+=command;if(photoMode!="SUCCESS")throw ManagementFailure(photoMode)
                        return PhotoAck(command.expectedVersion+1,3)
                    }
                },object:ProductPhotoProcessor{override suspend fun prepare(uri:String):ByteArray{prepared++;return byteArrayOf(1)}}) as T
            }
        })[CatalogEditorViewModel::class.java]
    }
    private fun command()=CatalogMutation(kind=CatalogRecord.Category,id=category.info.id,expectedVersion=1,name="Changed",sortOrder=0)

    @Test fun uncertainCatalogRetryKeepsPayloadAndAwaitsActualPull()=runTest(dispatcher){
        val vm=model();runCurrent();mode="UNAVAILABLE";val change=command();vm.save(change);runCurrent()
        assertEquals(change,vm.state.value.pending);assertFalse(vm.state.value.editable)
        vm.save(change.copy(name="Must not submit"));runCurrent();assertEquals(1,sent.size)
        mode="SUCCESS";vm.retryCommand();runCurrent();assertEquals(sent[0],sent[1]);assertEquals(3L,vm.state.value.waitingThrough)
        assertFalse(vm.state.value.editable);assertEquals(change.operationId,vm.state.value.completedOperationId)
        pullState.value=pullState.value.copy(seq=3,lastSuccessMs=101);runCurrent();assertNull(vm.state.value.waitingThrough);assertTrue(vm.state.value.editable)
    }
    @Test fun conflictNeedsObservedSuccessfulRefreshAndRetainsDraftForReview()=runTest(dispatcher){
        val vm=model();runCurrent();vm.editor.open(command());vm.editor.update{it.copy(name="Unsaved")};mode="CONFLICT";vm.save(command());runCurrent()
        assertTrue(vm.state.value.refreshRequired);assertTrue(vm.editor.state.value.needsReview);assertFalse(vm.state.value.editable)
        vm.retrySync();runCurrent();assertTrue(vm.state.value.refreshing);assertTrue(vm.state.value.refreshRequired)
        pullState.value=pullState.value.copy(error="UNAVAILABLE");runCurrent();assertTrue(vm.state.value.refreshRequired)
        catalog.value=catalog.value.copy(categories=listOf(category.copy(info=category.info.copy(version=2,name="Latest"))))
        pullState.value=pullState.value.copy(lastSuccessMs=101,error=null);runCurrent()
        assertFalse(vm.state.value.refreshRequired);assertFalse(vm.state.value.refreshing);assertTrue(vm.state.value.editable)
        assertEquals("Latest",vm.state.value.catalog.categories.single().info.name)
        assertEquals("Unsaved",vm.editor.state.value.draft?.name);assertTrue(vm.editor.state.value.needsReview)
    }
    @Test fun invalidMutationIsDefinitiveAndCorrectedSaveUsesNewOperation()=runTest(dispatcher){
        val vm=model();runCurrent();mode="INVALID";val change=command();vm.save(change);runCurrent()
        assertNull(vm.state.value.pending);assertTrue(vm.state.value.editable)
        mode="SUCCESS";val corrected=change.copy(operationId=java.util.UUID.randomUUID().toString(),name="Corrected")
        vm.save(corrected);runCurrent();assertEquals(2,sent.size);assertNotEquals(sent[0].operationId,sent[1].operationId)
    }
    @Test fun photoRetryIsIndependentAndCannotCloseUnsavedProductDraft()=runTest(dispatcher){
        val vm=model();runCurrent();vm.editor.open(CatalogMutation(kind=CatalogRecord.Product,id=product.info.id,expectedVersion=2,name=product.info.name,priceIdr=6000,parentId=category.info.id))
        vm.editor.update{it.copy(name="Unsaved product name")};photoMode="UNAVAILABLE";vm.removePhoto(product);runCurrent()
        val first=requireNotNull(vm.state.value.photoCommand);assertEquals(3L,first.expectedVersion);assertNull(vm.state.value.completedOperationId)
        photoMode="SUCCESS";vm.retryPhoto();runCurrent();assertEquals(photoSent[0],photoSent[1]);assertEquals(3L,vm.state.value.waitingThrough)
        pullState.value=pullState.value.copy(seq=3,lastSuccessMs=101);runCurrent();assertEquals("Unsaved product name",vm.editor.state.value.draft?.name)
        assertTrue(sent.isEmpty());assertEquals(2,photoSent.size)
    }
    @Test fun pickerCancelDoesNotPrepareUploadOrPublish()=runTest(dispatcher){
        val vm=model();runCurrent();assertTrue(vm.beginPhotoPick(product));vm.photoPicked(null);runCurrent()
        assertEquals(0,prepared);assertTrue(photoSent.isEmpty());assertNull(vm.state.value.photoCommand)
    }
    @Test fun slowCancelledIoCannotKeepOldCatalogVisibleInNewScope()=runTest(dispatcher){
        val vm=model();runCurrent();pause=CompletableDeferred();ignoreCancellation=true;vm.save(command());runCurrent()
        auth.state.value=auth.state.value.copy(membership=membership.copy(outletId="outlet-b"));runCurrent()
        assertNull(vm.state.value.scope);assertTrue(vm.state.value.loading);assertTrue(vm.state.value.catalog.products.isEmpty())
        pause!!.complete(Unit);runCurrent()
        assertEquals("outlet-b",vm.state.value.scope?.sale?.outletId);assertNull(vm.state.value.pending);assertNull(vm.state.value.completedOperationId)
    }
    @Test fun scopeChangeCancelsInflightButKeepsOriginalUncertainWorkScoped()=runTest(dispatcher){
        val vm=model();runCurrent();val oldScope=vm.state.value.scope;pause=CompletableDeferred();val change=command();vm.editor.open(change);vm.save(change);runCurrent();assertTrue(vm.state.value.busy)
        auth.state.value=auth.state.value.copy(membership=membership.copy(outletId="outlet-b"));runCurrent()
        assertEquals("outlet-b",vm.state.value.scope?.sale?.outletId);assertNull(vm.editor.state.value.draft);assertNull(vm.state.value.pending)
        auth.state.value=auth.state.value.copy(membership=membership);runCurrent()
        assertEquals(oldScope,vm.state.value.scope);assertEquals(change,vm.state.value.pending)
        auth.state.value=AuthState(AuthStage.LOGIN,true);runCurrent();assertNull(vm.state.value.pending);assertNull(vm.editor.state.value.draft)
    }
}
