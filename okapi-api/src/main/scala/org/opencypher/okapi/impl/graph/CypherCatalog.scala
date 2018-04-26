/*
 * Copyright (c) 2016-2018 "Neo4j Sweden, AB" [https://neo4j.com]
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
 *
 * Attribution Notice under the terms of the Apache License 2.0
 *
 * This work was created by the collective efforts of the openCypher community.
 * Without limiting the terms of Section 6, any Derivative Work that is not
 * approved by the public consensus process of the openCypher Implementers Group
 * should not be described as “Cypher” (and Cypher® is a registered trademark of
 * Neo4j Inc.) or as "openCypher". Extensions by implementers or prototypes or
 * proposals for change that have been documented or implemented should only be
 * described as "implementation extensions to Cypher" or as "proposed changes to
 * Cypher that are not yet approved by the openCypher community".
 */
package org.opencypher.okapi.impl.graph

import org.opencypher.okapi.api.graph.{Namespace, PropertyGraph, PropertyGraphCatalog, QualifiedGraphName}
import org.opencypher.okapi.api.io.PropertyGraphDataSource
import org.opencypher.okapi.impl.exception.{IllegalArgumentException, UnsupportedOperationException}
import org.opencypher.okapi.impl.io.SessionGraphDataSource

/**
  * This is the default implementation of the [[org.opencypher.okapi.api.graph.PropertyGraphCatalog]].
  * It uses a mutable mapping to store the mapping between
  * [[org.opencypher.okapi.api.graph.Namespace]]s and [[org.opencypher.okapi.api.io.PropertyGraphDataSource]]s.
  *
  * By default this catalog mounts a single [[org.opencypher.okapi.impl.io.SessionGraphDataSource]] under the namespace
  * [[org.opencypher.okapi.impl.graph.CypherCatalog#sessionNamespace]]. This PGDS is used to store session local graphs.
  */
class CypherCatalog extends PropertyGraphCatalog{

  /**
    * The [[org.opencypher.okapi.api.graph.Namespace]] used to store graphs within this session.
    *
    * @return session namespace
    */
  def sessionNamespace: Namespace = SessionGraphDataSource.Namespace

  /**
    * Stores a mutable mapping between a [[org.opencypher.okapi.api.graph.Namespace]] and the specific
    * [[org.opencypher.okapi.api.io.PropertyGraphDataSource]].
    *
    * This mapping also holds the [[org.opencypher.okapi.impl.io.SessionGraphDataSource]] by default.
    */
  private var dataSourceMapping: Map[Namespace, PropertyGraphDataSource] =
    Map(sessionNamespace -> new SessionGraphDataSource)

  override def namespaces: Set[Namespace] = dataSourceMapping.keySet

  override def source(namespace: Namespace): PropertyGraphDataSource = dataSourceMapping.getOrElse(namespace,
    throw IllegalArgumentException(s"a data source registered with namespace '$namespace'"))

  override def listSources: Map[Namespace, PropertyGraphDataSource] = dataSourceMapping

  override def register(namespace: Namespace, dataSource: PropertyGraphDataSource): Unit = dataSourceMapping.get(namespace) match {
    case Some(p) => throw IllegalArgumentException(s"no data source registered with namespace '$namespace'", p)
    case None => dataSourceMapping = dataSourceMapping.updated(namespace, dataSource)
  }

  override def deregister(namespace: Namespace): Unit = {
    if (namespace == sessionNamespace) throw UnsupportedOperationException("de-registering the session data source")
    dataSourceMapping.get(namespace) match {
      case Some(_) => dataSourceMapping = dataSourceMapping - namespace
      case None => throw IllegalArgumentException(s"a data source registered with namespace '$namespace'")
    }
  }

  override def graphNames: Set[QualifiedGraphName] =
    dataSourceMapping.flatMap {
      case (namespace, pgds) =>
        pgds.graphNames.map(n => QualifiedGraphName(namespace, n))
    }.toSet

  override def store(qualifiedGraphName: QualifiedGraphName, graph: PropertyGraph): Unit =
    source(qualifiedGraphName.namespace).store(qualifiedGraphName.graphName, graph)

  override def delete(qualifiedGraphName: QualifiedGraphName): Unit =
    source(qualifiedGraphName.namespace).delete(qualifiedGraphName.graphName)

  override def graph(qualifiedGraphName: QualifiedGraphName): PropertyGraph =
    source(qualifiedGraphName.namespace).graph(qualifiedGraphName.graphName)

}
