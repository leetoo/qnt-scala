package qnt.bz

trait Slice1dOps[K, S] {
  def size: Int

  def iloc(vals: Int*): S = iloc(vals.toIndexedSeq)
  def iloc(vals: IndexedSeq[Int]): S
  def iloc(start: Int, end: Int, step: Int, keepStart: Boolean, keepEnd: Boolean, round: Boolean)
  : S = iloc(RoundArrayRange(size, start, end, step, keepStart, keepEnd, round))

  def mask(m: Boolean*): S = mask(m.toIndexedSeq)
  def mask(m: IndexedSeq[Boolean]): S = {
    val idx = m.iterator.zipWithIndex.filter(_._1).map(_._2).toArray
    iloc(idx)
  }

  def loc(vals: K*):S = loc(vals.toIndexedSeq)
  def loc(vals: IndexedSeq[K]): S
  def loc(start: K, end: K, step: Int = 1, keepStart: Boolean = true, keepEnd: Boolean = true, round: Boolean = true): S
}
