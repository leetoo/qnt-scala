package qnt.breeze

import breeze.linalg.VectorLike

import scala.reflect.ClassTag

abstract class AbstractIndexVector[V]()(implicit ord: Ordering[V], tag: ClassTag[V]) extends breeze.linalg.Vector[V]
  with VectorLike[V, AbstractIndexVector[V]]
  with Loc1dOps[V, Int, SliceIndexVector[V]]
  with Iloc1dOps[Int, V, SliceIndexVector[V]]
{

  def unique: Boolean
  def ordered: Boolean
  def reversed: Boolean

  override def activeSize: Int = length
  override def activeIterator: Iterator[(Int, V)] = iterator
  override def activeValuesIterator: Iterator[V] = valuesIterator
  override def activeKeysIterator: Iterator[Int] = keysIterator
  override def repr: AbstractIndexVector[V] = this

  override def toString: String = toString(5, 5)

  def toString(head: Int, tail:Int, format:V=>String = _.toString): String = {
    val rows = Math.min(size, head+tail+1)
    val output = Array.ofDim[String](rows+1)
    output(0) = s"Index[${tag.runtimeClass.getSimpleName}]:"
    for(i <- 0 until rows) {
      val j = if (i < head) i else (size - rows + i)
      output(1+i) = format(apply(j))
      if (rows < size && i == head) {
        output(1+i) = "..."
      }
    }
    output.mkString("\n")
  }

  override def copy: IndexVector[V] = IndexVector[V](toArray, unique, ordered, reversed)

  def merge(other: AbstractIndexVector[V]): AbstractIndexVector[V] = {
    var vals = Array.concat(toArray, other.toArray)
    if(unique) {
      vals = vals.distinct
    }
    if (ordered) {
      vals = vals.sorted(ord)
      if (reversed) {
        vals = vals.reverse
      }
    }
    IndexVector[V](vals, unique, ordered, reversed)
  }

  def indexOfExact(value: V): Option[Int]

  def indexOfExactUnsafe(value: V): Int

  // TODO tests
  def indexOfBinarySearch(value: V): BinarySearchResult = {
    if (!ordered) {
      throw new IllegalStateException("unordered")
    }

    if(length < 1) {
      return BinarySearchResult(false,false)
    }

    var leftLeftIdx = 0
    var rightRightIdx = this.length - 1

    var leftVal: V = apply(leftLeftIdx)
    var rightVal: V = apply(rightRightIdx)

    @inline
    def findSameRightIdx(leftIdx: Int, leftVal: V) = {
      var rightIdx = leftIdx
      if (!unique) {
        do rightIdx += 1
        while (rightIdx < length && apply(rightIdx) == leftVal)
        rightIdx -= 1
      }
      rightIdx
    }

    @inline
    def findSameLeftIdx(rightIdx: Int, rightVal: V) = {
      var leftIdx = rightIdx
      if (!unique) {
        do leftIdx -= 1
        while (rightIdx > 0 && apply(leftIdx) == rightVal)
        leftIdx += 1
      }
      leftIdx
    }

    var leftRightIdx = findSameRightIdx(leftLeftIdx, leftVal)
    var rightLeftIdx = findSameLeftIdx(rightRightIdx, rightVal)

    if (leftVal == value) {
      return BinarySearchResult(true, false, leftLeftIdx, leftRightIdx)
    }

    if (rightVal == value) {
      return BinarySearchResult(true, false, leftLeftIdx, rightLeftIdx)
    }

    if (ord.lt(rightVal, value) ^ reversed) {
      return BinarySearchResult(false, false)
    }

    if (ord.gt(leftVal, value) ^ reversed) {
      return BinarySearchResult(false, false)
    }

    while (rightLeftIdx - leftRightIdx > 0) {
      val midIdx = (rightLeftIdx + leftRightIdx) / 2
      val midVal = apply(midIdx)

      var midLeftIdx = findSameLeftIdx(midIdx, midVal)
      var midRightIdx = findSameRightIdx(midIdx, midVal)

      if (midVal == value) {
        return BinarySearchResult(true, false, midLeftIdx, midRightIdx)
      } else if (ord.lt(midVal, value) ^ reversed) {
        leftLeftIdx = midLeftIdx
        leftRightIdx = midRightIdx
        leftVal = midVal
      } else if (ord.gt(midVal, value) ^ reversed) {
        rightRightIdx = midRightIdx
        rightLeftIdx = midLeftIdx
        rightVal = midVal
      }
    }
    BinarySearchResult(false, true, leftRightIdx, rightLeftIdx)
  }

  override def at(v: V): Option[Int] = indexOfExact(v)

  override def iat(i: Int): V = apply(i)

  case class BinarySearchResult(
     foundValue: Boolean,
     foundRange: Boolean,
     start:Int = -1,
     end:Int = -1
   ){
    def found:Boolean = foundValue || foundRange
    def notFound:Boolean = !found
  }

  override def loc(vals: IterableOnce[V]): SliceIndexVector[V] = {
    iloc(vals.iterator.map(indexOfExact).filter(_.isDefined).map(_.get))
  }

  override def loc(start: V, end: V, step: Int = 1, keepStart: Boolean = true, keepEnd: Boolean = true,
                   round: Boolean = true): SliceIndexVector[V] = {
      val startIdx = indexOfBinarySearch(start)
      val endIdx = indexOfBinarySearch(end)
      if(startIdx.notFound || endIdx.notFound) {
        AbstractIndexVector.empty[V].iloc()
      } else {
        iloc(
          if(step > 0)
            if(!unique && keepStart && startIdx.foundValue) startIdx.start else startIdx.end
          else
            if(!unique && keepEnd && startIdx.foundValue) startIdx.end else startIdx.start
          ,
          if(step > 0)
            if(!unique && keepEnd && endIdx.foundValue) endIdx.end else endIdx.start
          else
            if(!unique && keepStart && endIdx.foundValue) endIdx.start else endIdx.end
          ,
          step,
          keepStart, keepEnd, round
        )
      }
  }

  override def iloc(idx: IterableOnce[Int]): SliceIndexVector[V] = SliceIndexVector[V](this, idx)

  def contains(v: V): Boolean

  def toSet: Set[V] = if(unique) indexSet else throw new IllegalArgumentException("not unique")

  private object indexSet extends Set[V] {

    override def incl(elem: V): Set[V] = Set() ++ iterator + elem

    override def excl(elem: V): Set[V] = Set() ++ iterator - elem

    override def contains(elem: V): Boolean = contains(elem)

    override def iterator: Iterator[V] = valuesIterator
  }
}

object AbstractIndexVector {
  def empty[V](implicit ord: Ordering[V], tag: ClassTag[V])
   = new IndexVector[V](Array.empty[V],  true,true, false)(ord, tag)
}
