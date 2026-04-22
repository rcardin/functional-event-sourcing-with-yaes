package in.rcard.fes.utils

import in.rcard.yaes.Reader

def reader[A](_value: => A): Reader[A] = new Reader[A] {
  override def value: A = _value
}
