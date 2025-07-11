// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.util.NonCodeUsageInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PsiSourcedPolySymbolReference(
  private val symbol: PolySymbol,
  private val sourceElement: PsiElement,
  private val host: PsiExternalReferenceHost,
  private val range: TextRange,
) : PsiReference {

  internal val newName: Ref<String> = Ref()

  fun createRenameHandler(): RenameHandler =
    RenameHandler(this)

  override fun getElement(): PsiElement =
    host

  override fun getRangeInElement(): TextRange =
    range

  override fun resolve(): PsiElement =
    sourceElement

  override fun getCanonicalText(): String =
    (sourceElement as? PsiNamedElement)?.name
    ?: range.substring(host.text)

  override fun handleElementRename(newElementName: String): PsiElement {
    newName.set(newElementName)
    return host
  }

  override fun bindToElement(element: PsiElement): PsiElement =
    element

  override fun isReferenceTo(element: PsiElement): Boolean =
    element.isEquivalentTo(sourceElement) || sourceElement.isEquivalentTo(element)

  override fun isSoft(): Boolean =
    false

  class RenameHandler(reference: PsiSourcedPolySymbolReference) {
    private val symbol = reference.symbol
    private val targetPointer = reference.resolve()
      .let { if (it is FakePsiElement) it.context ?: it else it }
      .createSmartPointer()
    private val rangePointer = SmartPointerManager.getInstance(reference.element.project).createSmartPsiFileRangePointer(
      reference.element.containingFile, reference.rangeInElement.shiftRight(reference.element.startOffset)
    )
    private val nameRef = reference.newName

    fun handleRename(): NonCodeUsageInfo? {
      val range = rangePointer.psiRange ?: return null
      val file = rangePointer.element ?: return null
      val newName = nameRef.get() ?: return null
      val target = targetPointer.dereference() ?: return null
      val queryExecutor = PolySymbolQueryExecutorFactory.create(
        PsiTreeUtil.findElementOfClassAtRange(file, range.startOffset, range.endOffset, PsiElement::class.java)
        ?: file
      )
      return NonCodeUsageInfo.create(file, range.startOffset, range.endOffset, target,
                                     symbol.adjustNameForRefactoring(
                                       queryExecutor,
                                       newName,
                                       file.text.substring(range.startOffset, range.endOffset)))
    }
  }
}