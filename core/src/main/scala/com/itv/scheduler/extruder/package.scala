package com.itv.scheduler

package object extruder {
  object primitives extends PrimitiveEncoders with PrimitiveDecoders

  object semiauto extends DerivedDecoders with DerivedEncoders with DeriveSyntax

  object derivingImplicits extends DerivedDecoders with DerivedEncoders
}
