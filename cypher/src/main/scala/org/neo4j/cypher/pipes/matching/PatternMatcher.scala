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

import org.neo4j.graphdb.Node

class PatternMatcher(startPoint: PatternNode, bindings: Map[String, Any]) extends Traversable[Map[String, Any]] {

  def foreach[U](f: (Map[String, Any]) => U) {
    traverse(MatchingPair(startPoint, startPoint.pinnedEntity.get), Seq(), Seq(), f)
  }

  private def traverse[U](current: MatchingPair,
                          history: Seq[MatchingPair],
                          future: Seq[MatchingPair],
                          yielder: Map[String, Any] => U) {

    val patternNode: PatternNode = current.patternElement.asInstanceOf[PatternNode]
    val node: Node = current.entity.asInstanceOf[Node]

    bindings.get(patternNode.key) match {
      case Some(pinnedNode) => if (pinnedNode != node) return
      case None =>
    }

    patternNode.getPRels(history).toList match {
      case pRel :: tail => visitNext(patternNode, node, pRel, history,
        future ++ Seq(MatchingPair(patternNode, node)), yielder)

      case List() => future.toList match {
        case List() => yieldThis(yielder, history ++ Seq(MatchingPair(patternNode, node)))
        case next :: rest => traverse(next, history ++ Seq(MatchingPair(patternNode, node)), rest, yielder)
      }
    }
  }

  private def visitNext[U](patternNode: PatternNode,
                           node: Node,
                           pRel: PatternRelationship,
                           history: Seq[MatchingPair],
                           future: Seq[MatchingPair],
                           yielder: (Map[String, Any]) => U) {

    val notVisitedRelationships = patternNode.getRealRelationships(node, pRel, history)
    notVisitedRelationships.foreach(rel => {
      val nextNode = rel.getOtherNode(node)
      val nextPNode = pRel.getOtherNode(patternNode)
      val newHistory = history ++ Seq(MatchingPair(patternNode, node), MatchingPair(pRel, rel))
      traverse(MatchingPair(nextPNode, nextNode), newHistory, future, yielder)
    })

  }

  private def yieldThis[U](yielder: Map[String, Any] => U, history: Seq[Any]) {
    val resultMap = history.map(_ match {
      case MatchingPair(p, e) => (p.key, e)
    }).toMap

    yielder(resultMap)
  }
}