/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.expression.proxies.primitives.operations._
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise._
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric._

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[BitwiseAndTest],
    classOf[BitwiseOrTest],
    classOf[BitwiseShiftRightTest],
    classOf[BitwiseShiftLeftWithZerosTest],
    classOf[BitwiseShiftRightWithZerosTest],
    classOf[BitwiseXorTest]))
class BitwiseOperationsTestSuite
