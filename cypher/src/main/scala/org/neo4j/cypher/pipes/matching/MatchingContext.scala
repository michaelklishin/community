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
package org.neo4j.cypher.pipes.matching

import org.neo4j.cypher.SyntaxException
import org.neo4j.cypher.commands._
import collection.immutable.Map
import collection.{Traversable, Seq}
import org.neo4j.cypher.symbols.{NodeType, SymbolTable}

class MatchingContext(patterns: Seq[Pattern], boundIdentifiers: SymbolTable, clauses: Seq[Clause] = Seq()) {
  val patternGraph = buildPatternGraph()

  def getMatches(sourceRow: Map[String, Any]): Traversable[Map[String, Any]] = {


    val builder = new PatterMatchingBuilder(patternGraph, clauses)
    builder.build(sourceRow)
  }


  /*
  This method is mutable, but it is only called from the constructor of this class. The created pattern graph
   is immutable and thread safe.
   */
  private def buildPatternGraph(): PatternGraph = {
    val patternNodeMap: scala.collection.mutable.Map[String, PatternNode] = scala.collection.mutable.Map()
    val patternRelMap: scala.collection.mutable.Map[String, PatternRelationship] = scala.collection.mutable.Map()

    boundIdentifiers.identifiers.
      filter(_.typ == NodeType()). //Find all bound nodes...
      foreach(id => patternNodeMap(id.name) = new PatternNode(id.name)) //...and create patternNodes for them

    patterns.foreach(_ match {
      case RelatedTo(left, right, rel, relType, dir, optional) => {
        val leftNode: PatternNode = patternNodeMap.getOrElseUpdate(left, new PatternNode(left))
        val rightNode: PatternNode = patternNodeMap.getOrElseUpdate(right, new PatternNode(right))

        if (patternRelMap.contains(rel)) {
          throw new SyntaxException("Can't re-use pattern relationship '%s' with different start/end nodes.".format(rel))
        }

        patternRelMap(rel) = leftNode.relateTo(rel, rightNode, relType, dir, optional)
      }
      case VarLengthRelatedTo(pathName, start, end, minHops, maxHops, relType, dir, iterableRel, optional) => {
        val startNode: PatternNode = patternNodeMap.getOrElseUpdate(start, new PatternNode(start))
        val endNode: PatternNode = patternNodeMap.getOrElseUpdate(end, new PatternNode(end))
        patternRelMap(pathName) = startNode.relateViaVariableLengthPathTo(pathName, endNode, minHops, maxHops, relType, dir, iterableRel, optional)
      }
      case _ =>
    })

    new PatternGraph(patternNodeMap.toMap, patternRelMap.toMap, boundIdentifiers)
  }
}

