package com.dauntehnusantara.pos.feature.checkout.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope

interface AuthenticatedCheckoutFactory { fun open(scope: PullScope): CheckoutRepository }
