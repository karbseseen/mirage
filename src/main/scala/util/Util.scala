package util


inline def loop(from: Int, until: Int, step: Int = 1)(inline func: Int => Unit): Unit =
  var index = from
  while (index < until)
    func(index)
    index += step

inline def loop(count: Int)(inline func: Int => Unit): Unit =
  loop(0, count)(func)
