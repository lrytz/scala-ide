/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.Names

trait UnboundValuesSupport {

  val toolbox: ToolBox[universe.type]

  import toolbox.u._

  /**
   * Keeps track of all enclosing trees
   * @param topLevelTree top level tree
   */
  class ScopeManager(topLevelTree: Tree) {

    private var treeStack: List[Tree] = Nil

    final def popTree(): Unit = treeStack = treeStack.tail

    final def pushTree(tree: Tree): Unit = treeStack = tree :: treeStack

    final def insideImport: Boolean = treeStack.exists {
      case Import(_, _) => true
      case _ => false
    }

    /**
     * @return tree defining enclosing scope e.g.: function, block, case def
     *         when no such tree is found the top level tree is returned
     */
    final def findCurrentScopeTree(): Tree =
      treeStack.find {
        case _: Function | _: Block | _: CaseDef => true
        case _ => false
      }.getOrElse(topLevelTree)
  }

  /**
   * Keeps track of bound and unbound variables (name and type if is accessible)
   */
  private class VariableManager(extractType: Tree => Option[String]) {

    /**
     * Unbound names in a sense that a name is unbound if there exists at least one scope in which it is unbound.
     * NOTE: In some other scopes in a processed code snippet the unbound name might be actually bound
     */
    private var _unboundNames = Map.empty[TermName, Option[String]]

    /**
     * A map of names and corresponding trees which bind the name
     */
    private var boundNames = Map.empty[Name, Seq[Tree]].withDefaultValue(Seq.empty)

    /**
     * @return collected unbound names
     */
    final def unboundNames(): Map[TermName, Option[String]] = _unboundNames

    /**
     * @param name identifier
     * @param boundingTree tree bounding the name
     */
    final def registerNameBinding(name: Name, boundingTree: Tree): Unit = {
      boundNames += name -> (boundNames(name) :+ boundingTree)
    }

    /**
     * Checks whether name is unbound and if so registers it as such
     * @param name an identifier
     * @param tree tree representing the name usage
     */
    final def registerUnboundName(name: TermName, tree: Tree): Unit = {
      val isBound = boundingTreesOf(name).exists(parentOf(tree))
      val isScalaSymbol = name == termNames.WILDCARD || name == Names.Scala.scalaPackageTermName
      val isSymbolVisibleByDefault = isVisibleByDefault(name.toString)

      if (!isScalaSymbol && !isSymbolVisibleByDefault && !isBound && name.isTermName) {
        _unboundNames += name -> extractType(tree)
      }
    }

    /** Checks if type is defined in `scala.Predef` or in `scala` package object. */
    private def isVisibleByDefault(typeName: String): Boolean =
      defaultSymbols.contains(typeName)

    private lazy val defaultSymbols = {
      val predefSymbols = {
        val clazz = Predef.getClass
        val methods = clazz.getMethods.map(_.getName)
        val fields = clazz.getFields.map(_.getName)
        methods ++ fields
      }
      val fromPackageObject = {
        val clazz = getClass.getClassLoader.loadClass("scala.package$")
        val methods = clazz.getMethods.map(_.getName)
        val fields = clazz.getFields.map(_.getName)
        methods ++ fields
      }
      (predefSymbols ++ fromPackageObject).toSet
    }

    /**
     * @param child child searching for a parent
     * @param node node checked for parenthood
     * @return true if the node contains the child, i.e. the node is a parent of the child, false otherwise
     */
    private def parentOf(child: Tree)(node: Tree): Boolean = node.exists(_ == child)

    /**
     * @param name identifier
     * @return all trees which define the name
     */
    private def boundingTreesOf(name: Name): Seq[Tree] = boundNames(name)

  }

  /**
   * Collects unbound names in the tree.
   */
  class VariableProxyTraverser(tree: Tree, extractType: Tree => Option[String] = _ => None) extends Traverser {

    private val scopeManager = new ScopeManager(tree)

    private val nameManager = new VariableManager(extractType)

    /**
     * Collects unbound names in the tree.
     */
    final def findUnboundNames(): Set[TermName] = {
      findUnboundValues.keySet
    }

    /**
     * Collects unbound values (name, type) in the tree.
     */
    final def findUnboundValues(): Map[TermName, Option[String]] = {
      this.traverse(tree)
      nameManager.unboundNames()
    }

    /**
     * Collects unbound names in tree.
     * Keeps track of all name bindings in order to collect unbound names.
     */
    final override def traverse(tree: Tree): Unit = {
      scopeManager.pushTree(tree)
      tree match {
        // all identifiers
        case Assign(Ident(termName), value) =>
          // suppressing value extraction from lhs
          super.traverse(value)

        case Ident(name: TermName) if !scopeManager.insideImport =>
          nameManager.registerUnboundName(name, tree)

        // like: case ala: Ala =>
        case CaseDef(Bind(name, _), _, _) =>
          nameManager.registerNameBinding(name, tree)
          super.traverse(tree)

        // named args like: foo(ala = "ola")
        case AssignOrNamedArg(Ident(name), _) =>
          nameManager.registerNameBinding(name, scopeManager.findCurrentScopeTree())
          super.traverse(tree)

        // for assignments like: var ala; ala = "ola"
        case Bind(name, _) =>
          nameManager.registerNameBinding(name, scopeManager.findCurrentScopeTree())
          super.traverse(tree)

        // value definition like: val ala = "Ala"
        case restTree @ ValDef(_, name, _, impl) =>
          nameManager.registerNameBinding(name, scopeManager.findCurrentScopeTree())
          super.traverse(impl)

        //typed expression like x: Int
        case restTree @ Typed(impl, _) =>
          super.traverse(impl)

        case _ => super.traverse(tree)
      }
      scopeManager.popTree()
    }
  }

}
