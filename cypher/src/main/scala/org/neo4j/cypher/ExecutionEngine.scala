/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher

import commands._
import pipes._
import scala.collection.JavaConverters._
import org.neo4j.graphdb._
import org.neo4j.graphmatching.{PatternRelationship, PatternNode, AbstractPatternObject}
import java.lang.Iterable
import collection.Seq


class ExecutionEngine(val graph: GraphDatabaseService)
{
  @throws(classOf[SyntaxError])
  def execute(query: Query): ExecutionResult = query match
  {
    case Query(returns, start, matching, where, aggregation, sort) =>
    {
      val symbolTable = new SymbolTable

      val startPipe: Pipe = createSourcePumps(start).reduceLeft(_ ++ _)
      startPipe.prepare(symbolTable)

      val patternPipe = new PatternPipe(startPipe,matching)
      patternPipe.prepare(symbolTable)

      val filterPipe = new FilterPipe(where,patternPipe)
      filterPipe.prepare(symbolTable)

      val transformPipe = new TransformPipe(returns.returnItems,filterPipe) with ExecutionResult
      transformPipe.prepare(symbolTable)

      return transformPipe
    }
  }

  private def createSourcePumps(from: Start): Seq[Pipe] =
    from.startItems.map((item) =>
    {
      item match
      {
        case NodeByIndex(varName, idxName, key, value) =>
        {
          val indexHits: Iterable[Node] = graph.index.forNodes(idxName).get(key, value)
          new StartPipe(varName, indexHits.asScala.toList)
        }
        case NodeById(varName, ids@_*) => new StartPipe(varName, ids.map(graph.getNodeById))
        case RelationshipById(varName, ids@_*) => new StartPipe(varName, ids.map(graph.getRelationshipById))
      }
    })

}