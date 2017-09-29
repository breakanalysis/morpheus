/**
 * Copyright (c) 2016-2017 "Neo4j, Inc." [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.caps.api.spark

import org.opencypher.caps.api.expr._
import org.opencypher.caps.api.graph.CypherGraph
import org.opencypher.caps.api.record._
import org.opencypher.caps.api.schema.Schema
import org.opencypher.caps.api.types.{CTNode, CTRelationship}
import org.opencypher.caps.impl.record.CAPSRecordsTokens
import org.opencypher.caps.impl.spark.exception.Raise
import org.opencypher.caps.ir.api.global.TokenRegistry

trait CAPSGraph extends CypherGraph with Serializable {

  self =>

  final override type Graph = CAPSGraph
  final override type Records = CAPSRecords
  final override type Session = CAPSSession
  final override type Result = CAPSResult

  def tokens: CAPSRecordsTokens
}

object CAPSGraph {

  def empty(implicit caps: CAPSSession): CAPSGraph =
    new EmptyGraph() {
      override protected def graph = this

      override def session = caps

      override val tokens = CAPSRecordsTokens(TokenRegistry.empty)
    }

  def create(nodes: NodeScan, scans: GraphScan*)(implicit caps: CAPSSession): CAPSGraph = {
    val allScans = nodes +: scans
    val schema = allScans.map(_.schema).reduce(_ ++ _)
    val tokens = CAPSRecordsTokens(TokenRegistry.fromSchema(schema))
    new ScanGraph(allScans, schema, tokens)
  }

  def create(records: CAPSRecords, schema: Schema)
    (implicit caps: CAPSSession): CAPSGraph = {

    new PatternGraph(records, schema, CAPSRecordsTokens(TokenRegistry.fromSchema(schema)))
  }

  def createLazy(theSchema: Schema)(loadGraph: => CAPSGraph)(implicit caps: CAPSSession) = new CAPSGraph {
    override protected lazy val graph: CAPSGraph = {
      val g = loadGraph
      if (g.schema == theSchema) g else Raise.schemaMismatch()
    }

    override def tokens: CAPSRecordsTokens = graph.tokens

    override def session: CAPSSession = caps

    override def schema: Schema = theSchema

    override def nodes(name: String, nodeCypherType: CTNode): CAPSRecords =
      graph.nodes(name, nodeCypherType)

    override def relationships(name: String, relCypherType: CTRelationship): CAPSRecords =
      graph.relationships(name, relCypherType)

    override def union(other: CAPSGraph): CAPSGraph =
      graph.union(other)
  }

  sealed abstract class EmptyGraph(implicit val caps: CAPSSession) extends CAPSGraph {

    override val schema = Schema.empty
    override val tokens = CAPSRecordsTokens(TokenRegistry.fromSchema(schema))

    override def nodes(name: String, cypherType: CTNode) =
      CAPSRecords.empty(RecordHeader.from(OpaqueField(Var(name)(cypherType))))

    override def relationships(name: String, cypherType: CTRelationship) =
      CAPSRecords.empty(RecordHeader.from(OpaqueField(Var(name)(cypherType))))

    override def union(other: CAPSGraph): CAPSGraph = other
  }
}
