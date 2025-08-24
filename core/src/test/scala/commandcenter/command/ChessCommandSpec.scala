package commandcenter.command

import commandcenter.CCRuntime.Env
import commandcenter.CommandBaseSpec
import zio.test.*

object ChessCommandSpec extends CommandBaseSpec {

  def spec: Spec[TestEnvironment & Env, Any] =
    suite("ChessCommandSpec")(
      test("parse FEN") {
        assertTrue(
          ChessCommand.ChessState.Fen.parse("4k2r/6r1/8/8/8/8/3R4/R3K3").isDefined,
          ChessCommand.ChessState.Fen.parse("4k2r/6r1/8/8/8/8/3R4/R3K3 w").isDefined,
          ChessCommand.ChessState.Fen.parse("4k2r/6r1/8/8/8/8/3R4/R3K3 w Qk").isDefined,
          ChessCommand.ChessState.Fen.parse("4k2r/6r1/8/8/8/8/3R4/R3K3 w Qk -").isDefined,
          ChessCommand.ChessState.Fen.parse("4k2r/6r1/8/8/8/8/3R4/R3K3 w Qk - 0 1").isDefined
        )
      },
      test("extract moves from a PGN") {
        val pgn = ChessCommand.ChessState.Pgn.parse(
          """|[Event "F/S Return Match"]
             |[Site "Belgrade, Serbia JUG"]
             |[Date "1992.11.04"]
             |[Round "29"]
             |[White "Fischer, Robert J."]
             |[Black "Spassky, Boris V."]
             |[Result "1/2-1/2"]
             |
             |1.e4 e5 2.Nf3 Nc6 3.Bb5 {This opening is called the Ruy Lopez.} 3...a6
             |4.Ba4 Nf6 5.O-O Be7 6.Re1 b5 7.Bb3 d6 8.c3 O-O 9.h3 Nb8 10.d4 Nbd7
             |11.c4 c6 12.cxb5 axb5 13.Nc3 Bb7 14.Bg5 b4 15.Nb1 h6 16.Bh4 c5 17.dxe5
             |Nxe4 18.Bxe7 Qxe7 19.exd6 Qf6 20.Nbd2 Nxd6 21.Nc4 Nxc4 22.Bxc4 Nb6
             |23.Ne5 Rae8 24.Bxf7+ Rxf7 25.Nxf7 Rxe1+ 26.Qxe1 Kxf7 27.Qe3 Qg5 28.Qxg5
             |hxg5 29.b3 Ke6 30.a3 Kd6 31.axb4 cxb4 32.Ra5 Nd5 33.f3 Bc8 34.Kf2 Bf5
             |35.Ra7 g6 36.Ra6+ Kc5 37.Ke1 Nf4 38.g3 Nxh3 39.Kd2 Kb5 40.Rd6 Kc5 41.Ra6
             |Nf2 42.g4 Bd3 43.Re6 1/2-1/2""".stripMargin
        )

        assertTrue(
          pgn.get.moves.startsWith("1.e4 ")
        )
      },
      test("extract moves from a raw move list") {
        val pgn = ChessCommand.ChessState.Pgn.parse(
          """|1. e4 c6 2. d4 d6 3. Nc3 Nf6 4. f4 Qb6 5. Nf3 Bg4 6. Be2 Nbd7 7. e5 Nd5 8. O-O Nc3 9. bc3 e6
             |10. Ng5 Be2 11. Qe2 h6 12. Nf7 Kf7 13. f5 de5 14. fe6 Ke6 15. Rb1 Qb1 16. Qc4 Kd6 17. Ba3 Kc7
             |18. Rb1 Ba3 19. Qb3 Be7 20. Qb7 Kd6 21. de5 Ne5 22. Rd1 Ke6 23. Qb3 Kf5 24. Rf1 Ke4 25. Re1 Kf5
             |26. g4 Kf6 27. Rf1 Kg6 28. Qe6 Kh7 29. Qe5 Rhe8 30. Rf7 Bf8 31. Qf5 Kg8 32. Kf2 Bc5 33. Kg3 Re3
             |34. Kh4 Rae8 35. Rg7 Kg7 36. Qc5 R8e6 37. Qa7 Kg6 38. Qa8 Kf6 39. a4 Ke5 40. a5 Kd5 41. Qd8 Ke4
             |42. a6 Kf3 43. a7 Re2 44. Qd3 R6e3 45. Qe3""".stripMargin
        )

        assertTrue(
          pgn.get.moves.startsWith("1. e4 ")
        )
      }
    )
}
