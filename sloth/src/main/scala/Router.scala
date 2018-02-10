package sloth.server

import sloth.core._
import sloth.internal.RouterMacro

import cats.Functor
import cats.syntax.functor._

trait Router[PickleType, Result[_]] { router =>
  def apply(request: Request[PickleType]): RouterResult[Result, PickleType]

  def route[T](value: T)(implicit functor: Functor[Result]): Router[PickleType, Result] = macro RouterMacro.impl[T, PickleType, Result]

  final def orElse(otherRouter: Router[PickleType, Result]) = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): RouterResult[Result, PickleType] = router(request) match {
      case RouterResult.Failure(_, ServerFailure.PathNotFound(_)) => otherRouter(request)
      case other => other
    }
  }

  final def map[R[_]](f: RouterResult[Result, PickleType] => RouterResult[R, PickleType]): Router[PickleType, R] = new Router[PickleType, R] {
    def apply(request: Request[PickleType]): RouterResult[R, PickleType] = f(router(request))
  }
}
object Router {
  def apply[PickleType, Result[_]] = new Router[PickleType, Result] {
    def apply(request: Request[PickleType]): RouterResult[Result, PickleType] = RouterResult.Failure(Nil, ServerFailure.PathNotFound(request.path))
  }
}

sealed trait RouterResult[+Result[_], PickleType] {
  def toEither: Either[ServerFailure, Result[PickleType]]
}
object RouterResult {
  case class Value[PickleType](raw: Any, serialized: PickleType)

  case class Failure[PickleType](arguments: List[List[Any]], failure: ServerFailure) extends RouterResult[Lambda[X => Nothing], PickleType] {
    def toEither = Left(failure)
  }
  case class Success[PickleType, Result[_] : Functor](arguments: List[List[Any]], result: Result[Value[PickleType]]) extends RouterResult[Result, PickleType] {
    def toEither = Right(result.map(_.serialized))
  }
}
