package com.dauntehnusantara.pos.core.common

import kotlinx.coroutines.flow.Flow
interface NetworkAvailability { val validated: Flow<Boolean> }
