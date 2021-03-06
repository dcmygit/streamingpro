package tech.mlsql.dsl.adaptor

import java.util.concurrent.atomic.AtomicInteger

import streaming.dsl.DslAdaptor
import streaming.dsl.parser.DSLSQLParser
import streaming.dsl.parser.DSLSQLParser._
import streaming.dsl.template.TemplateMerge
import tech.mlsql.dsl.processor.PreProcessListener

import scala.collection.mutable.ArrayBuffer

/**
  * 2019-04-11 WilliamZhu(allwefantasy@gmail.com)
  */
class CommandAdaptor(preProcessListener: PreProcessListener) extends DslAdaptor {

  def evaluate(str: String) = {
    TemplateMerge.merge(str, preProcessListener.scriptSQLExecListener.env().toMap)
  }

  override def parse(ctx: DSLSQLParser.SqlContext): Unit = {
    var command = ""
    var parameters = ArrayBuffer[String]()
    (0 to ctx.getChildCount() - 1).foreach { tokenIndex =>
      ctx.getChild(tokenIndex) match {
        case s: CommandContext =>
          command = s.getText.substring(1)
        case s: SetValueContext =>
          val oringinalText = s.getText
          parameters += cleanBlockStr(cleanStr(evaluate(oringinalText)))
        case s: SetKeyContext =>
          parameters += s.getText
        case _ =>
      }
    }
    val env = preProcessListener.scriptSQLExecListener.env()
    val tempCommand = env(command)
    var finalCommand = ArrayBuffer[Char]()
    val len = tempCommand.length

    def fetchParam(index: Int) = {
      if (index < parameters.length) {
        parameters(index).toCharArray
      } else {
        Array[Char]()
      }
    }


    val posCount = new AtomicInteger(0)
    val curPos = new AtomicInteger(0)

    def positionReplace(i: Int): Boolean = {
      if (tempCommand(i) == '{' && i < (len - 1) && tempCommand(i + 1) == '}') {
        finalCommand ++= fetchParam(posCount.get())
        curPos.set(i + 2)
        posCount.addAndGet(1)
        return true
      }
      return false
    }

    def namedPositionReplace(i: Int): Boolean = {

      if (tempCommand(i) != '{') return false

      val startPos = i
      var endPos = i


      // now , we should process with greedy until we meet '}'
      while (endPos < len - 1 && tempCommand(endPos) != '}') {
        endPos += 1
      }

      val shouldBeNumber = tempCommand.slice(startPos + 1, endPos).trim
      val namedPos = Integer.parseInt(shouldBeNumber)
      finalCommand ++= fetchParam(namedPos)
      curPos.set(endPos + 1)
      return true
    }

    (0 until len).foreach { i =>

      if (curPos.get() > i) {
      }
      else if (positionReplace(i)) {
      }
      else if (namedPositionReplace(i)) {

      } else {
        finalCommand += tempCommand(i)
      }
    }

    preProcessListener.addStatement(String.valueOf(finalCommand.toArray))

  }
}
