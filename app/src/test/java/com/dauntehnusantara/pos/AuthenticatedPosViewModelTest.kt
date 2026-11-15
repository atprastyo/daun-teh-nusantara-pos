package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.pos.presentation.*
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
@Config(sdk=[28],application=Application::class)
class AuthenticatedPosViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){store.clear();Dispatchers.resetMain()}
    private val member=Membership(DEMO_BRAND_ID,"Brand","outlet-a","Outlet A",Role.CASHIER)
    private val auth=object:AuthRepository {
        override val state=MutableStateFlow(AuthState(AuthStage.READY,true,membership=member,email="one@example.invalid"))
        override suspend fun restore(){};override suspend fun signIn(email:String,password:String){};override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){};override suspend fun refresh(){};override suspend fun unlockOffline(){};override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){};override suspend fun approveDevice(id:String){};override suspend fun signOut(){}
    }
    private val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Teh",version=1))
    private val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Melati",version=1),category.info.id,6000)
    private val menu=MutableStateFlow(Catalog(categories=listOf(category),products=listOf(product)))
    private val pullStatus=MutableStateFlow(PullStatus(1,true,1))
    private fun draft()=Draft(newId(),newId(),1,listOf(CheckoutRules.line(menu.value,product.info.id,emptyList(),1)))
    private inner class FakeCheckout:CheckoutRepository {
        val cart=MutableStateFlow(CheckoutState(draft(),null,0))
        val receipts=mutableListOf<Receipt>()
        val commands=mutableListOf<CheckoutCommand>()
        var mode="SAVED"
        var readable=true
        var pause:CompletableDeferred<Unit>?=null
        var ignoreCancellation=false
        var failAdd=false
        var historyPause:CompletableDeferred<Unit>?=null
        override fun observe():Flow<CheckoutState> = flow {check(readable);emitAll(cart)}
        override suspend fun add(draft:Draft,line:CartLine){if(failAdd)error("unavailable");cart.value=cart.value.copy(draft=draft.copy(revision=draft.revision+1,lines=draft.lines+line))}
        override suspend fun quantity(draft:Draft,lineId:String,quantity:Int){}
        override suspend fun remove(draft:Draft,lineId:String){}
        override suspend fun complete(command:CheckoutCommand):Receipt {
            commands+=command
            if(ignoreCancellation)withContext(NonCancellable){pause?.await()}else pause?.await()
            if(mode=="BEFORE")error("unknown save")
            if(mode=="CONFLICT")throw CheckoutConflict()
            val old=receipts.find{it.operationId==command.operationId}
            val r=old?:cart.value.draft.let{d->Receipt(d.id,d.operationId,member.brandId,member.outletId,"device","one@example.invalid",0,d.lines,d.totalIdr,CheckoutRules.payment(d.totalIdr,command.payment))}
            if(old==null)receipts+=r
            cart.value=CheckoutState(Draft(newId(),newId(),0,emptyList()),r,1)
            if(mode=="AFTER")error("enqueue failed after commit")
            return r
        }
        override suspend fun dismissReceipt(saleId:String){cart.value=cart.value.copy(receipt=null)}
        override suspend fun receipts():List<Receipt> { val hold=historyPause; if(hold!=null)withContext(NonCancellable){hold.await()}; return receipts.toList() }
        override suspend fun reconcile(command:CheckoutCommand):CheckoutResolution {
            if(!readable)return CheckoutResolution.Unknown
            receipts.find{it.operationId==command.operationId}?.let{return CheckoutResolution.Saved(it)}
            val d=cart.value.draft
            return if(d.id==command.draftId&&d.operationId==command.operationId&&d.revision==command.revision)CheckoutResolution.Unsaved else CheckoutResolution.Unknown
        }
    }
    private val repos=mutableMapOf<String,FakeCheckout>()
    private fun repo()=repos.getOrPut(auth.state.value.membership!!.outletId){FakeCheckout()}
    private var retries=0
    private fun model():AuthenticatedPosViewModel {
        val catalogRepo=object:CatalogRepository {
            override fun observe(brandId:String)=menu
            override suspend fun save(category:Category)=error("unused");override suspend fun save(product:Product)=error("unused")
            override suspend fun save(group:ModifierGroup)=error("unused");override suspend fun save(option:ModifierOption)=error("unused")
            override suspend fun assignGroups(brandId:String,productId:String,groupIds:List<String>,expectedVersion:Long){}
            override suspend fun archive(brandId:String,kind:CatalogRecord,id:String,expectedVersion:Long){};override suspend fun seedDemo(){}
        }
        return ViewModelProvider(store,object:ViewModelProvider.Factory {
            override fun<T:ViewModel>create(modelClass:Class<T>):T {
                @Suppress("UNCHECKED_CAST")
                return AuthenticatedPosViewModel(auth,object:PullGateway {
                    override suspend fun pullScope()=auth.state.value.membership?.let{PullScope(SaleScope(it.brandId,it.outletId,"device",auth.state.value.email),it.role.name,"https://example.invalid")}
                    override suspend fun fetch(scope:PullScope,after:Long,epoch:String?):PullPage=error("unused")
                },object:AuthenticatedCheckoutFactory{override fun open(scope:PullScope)=repo()},ObserveCatalog(catalogRepo),
                    object:PullRepository{override fun observe(scope:PullScope)=pullStatus;override suspend fun sync()=false},
                    object:SyncScheduler{override fun enqueue(force:Boolean){};override fun ensurePeriodic(){}},
                    object:SyncRepository {
                        override fun observe(scope:SaleScope)=flowOf(SyncStatus());override suspend fun record(receipt:Receipt){};override suspend fun drain()=false
                        override suspend fun retry(scope:SaleScope){retries++}
                    }) as T
            }
        })[AuthenticatedPosViewModel::class.java]
    }
    @Test fun failedSaveFreezesPaymentUntilReadbackAndSameCommandRetry()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();r.mode="BEFORE";val payment=PaymentInput(PaymentMethod.CASH,10000)
        vm.pay(payment);vm.pay(payment.copy(tenderedIdr=20000));runCurrent()
        assertEquals(1,r.commands.size);assertNotNull(vm.state.value.pending);assertTrue(vm.state.value.retryLocalAllowed);assertFalse(vm.state.value.canEdit)
        vm.pay(payment.copy(tenderedIdr=30000));vm.add(product.info.id,emptyList(),1);runCurrent();assertEquals(1,r.commands.size)
        r.mode="SAVED";vm.retryLocal();runCurrent();assertEquals(r.commands[0],r.commands[1]);assertEquals(1,r.receipts.size);assertNull(vm.state.value.pending);assertNotNull(vm.state.value.checkout?.receipt)
    }
    @Test fun enqueueErrorAfterCommitResolvesOriginalReceiptWithoutSecondPayment()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();r.mode="AFTER";vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent()
        assertEquals(1,r.receipts.size);assertNotNull(vm.state.value.checkout?.receipt);assertNull(vm.state.value.pending);vm.retryLocal();runCurrent();assertEquals(1,r.commands.size)
    }
    @Test fun unknownReadbackBlocksRetryUntilOriginalResultCanBeRead()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();r.mode="BEFORE";r.readable=false;vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent()
        assertNotNull(vm.state.value.pending);assertFalse(vm.state.value.retryLocalAllowed);vm.retryLocal();runCurrent();assertEquals(1,r.commands.size)
        r.readable=true;vm.reconcile();runCurrent();assertTrue(vm.state.value.retryLocalAllowed)
    }
    @Test fun changedDraftNeverEnablesRetryForUnmatchedFrozenCommand()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();r.mode="BEFORE";vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent()
        r.cart.value=r.cart.value.copy(draft=draft());vm.reconcile();runCurrent();assertFalse(vm.state.value.retryLocalAllowed);assertNotNull(vm.state.value.pending)
    }
    @Test fun catalogConflictRetainsOriginalUnpaidCartForReselection()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();val d=r.cart.value.draft;r.mode="CONFLICT";vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent()
        assertNull(vm.state.value.pending);assertEquals(PosError.CONFLICT,vm.state.value.error);assertEquals(d,vm.state.value.checkout?.draft)
    }
    @Test fun observedAddSuccessAndFailureAreDifferentResults()=runTest(dispatcher){
        val vm=model();runCurrent();repo().failAdd=true;vm.add(product.info.id,emptyList(),1);runCurrent();assertEquals(0L,vm.state.value.added)
        repo().failAdd=false;vm.add(product.info.id,emptyList(),1);runCurrent();assertEquals(1L,vm.state.value.added);assertEquals(2,vm.state.value.checkout?.draft?.lines?.size)
    }
    @Test fun readinessAndStaleCatalogCannotSubmitOrMutate()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();pullStatus.value=PullStatus(initialized=false,error="REBOOTSTRAP_REQUIRED");runCurrent()
        vm.pay(PaymentInput(PaymentMethod.CASH,10000));vm.add(product.info.id,emptyList(),1);runCurrent();assertTrue(r.commands.isEmpty());assertEquals(1,r.cart.value.draft.lines.size)
        pullStatus.value=PullStatus(initialized=true);menu.value=menu.value.copy(products=listOf(product.copy(basePriceIdr=7000)));runCurrent()
        assertTrue(vm.state.value.staleLineIds.isNotEmpty());vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent();assertTrue(r.commands.isEmpty())
    }
    @Test fun recoveryRetainsKnownReceiptButGuardsNextSale()=runTest(dispatcher){
        val vm=model();runCurrent();vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent();val original=vm.state.value.checkout?.receipt
        pullStatus.value=PullStatus(initialized=false,error="REBOOTSTRAP_REQUIRED");runCurrent();vm.nextSale();runCurrent();assertEquals(original,vm.state.value.checkout?.receipt)
    }
    @Test fun slowOldScopeActionCannotChangeNewOrRevisitedScope()=runTest(dispatcher){
        val vm=model();runCurrent();val old=repo();old.pause=CompletableDeferred();old.ignoreCancellation=true;vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent()
        auth.state.value=auth.state.value.copy(membership=member.copy(outletId="outlet-b"));runCurrent();assertNull(vm.state.value.pending);assertNull(vm.state.value.checkout?.receipt)
        val key=vm.state.value.scopeKey;old.pause!!.complete(Unit);runCurrent();assertEquals(key,vm.state.value.scopeKey);assertNull(vm.state.value.checkout?.receipt);assertFalse(vm.state.value.busy)
        auth.state.value=auth.state.value.copy(membership=member);runCurrent();assertNotNull(vm.state.value.checkout?.receipt);assertNull(vm.state.value.pending)
        auth.state.value=AuthState(AuthStage.LOGIN,true);runCurrent();assertNull(vm.state.value.checkout);assertTrue(vm.state.value.catalog.products.isEmpty())
    }
    @Test fun invalidCashAndUnconfirmedQrisNeverReachRepository()=runTest(dispatcher){
        val vm=model();runCurrent();vm.pay(PaymentInput(PaymentMethod.CASH,1));vm.pay(PaymentInput(PaymentMethod.QRIS_MANUAL));runCurrent();assertTrue(repo().commands.isEmpty());assertNull(vm.state.value.pending)
    }

    @Test fun lateSameScopeObservationCannotRestoreConsumedDraftAfterReceiptDismissal()=runTest(dispatcher){
        val vm=model();runCurrent();val r=repo();val oldDraft=r.cart.value.draft
        val hold=CompletableDeferred<Unit>();r.historyPause=hold;pullStatus.value=pullStatus.value.copy(lastSuccessMs=2);runCurrent()
        r.historyPause=null;vm.pay(PaymentInput(PaymentMethod.CASH,10000));runCurrent();assertNotNull(vm.state.value.checkout?.receipt)
        vm.nextSale();runCurrent();assertNull(vm.state.value.checkout?.receipt)
        val seen=mutableListOf<PosUiState>();backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect{seen+=it}}
        hold.complete(Unit);runCurrent()
        assertTrue(seen.none{it.checkout?.draft?.id==oldDraft.id});assertNull(vm.state.value.checkout?.receipt)
    }
}
