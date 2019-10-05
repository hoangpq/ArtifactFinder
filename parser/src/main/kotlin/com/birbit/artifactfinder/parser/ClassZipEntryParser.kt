package com.birbit.artifactfinder.parser

import com.birbit.artifactfinder.parser.vo.ArtifactInfoBuilder
import com.birbit.artifactfinder.parser.vo.ClassZipEntry
import kotlinx.metadata.jvm.KotlinClassMetadata
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

internal object ClassZipEntryParser {
    fun parse(classZipEntry: ClassZipEntry, builder: ArtifactInfoBuilder) {
        val node = ClassNode(Opcodes.ASM7)
        val reader = ClassReader(classZipEntry.stream)
        reader.accept(node, skippedParts)
        val metadata = node.kotlinMetadataAnnotation()

        if (metadata != null) {
            parseMetadata(metadata, node, builder)
        } else {
            parseJavaClass(node, builder)
        }
    }

    private fun parseJavaClass(
        node: ClassNode,
        into: ArtifactInfoBuilder
    ) {
        if (!node.isVisibleFromOutside()) return
        if (node.isInnerClass()) {
            into.add(node.toInnerClassInfo())
        } else {
            into.add(node.toClassInfo())
        }
    }

    private fun parseMetadata(
        metadata: KotlinClassMetadata,
        node: ClassNode,
        into: ArtifactInfoBuilder
    ) {
        val nodeInfo = node.toClassInfo()
        when (metadata) {
            is KotlinClassMetadata.Class -> {
                val kmClass = metadata.toKmClass()
                val isVisible = kmClass.isVisibleFromOutside() && node.isVisibleFromOutside()
                if (!isVisible) {
                    return
                }
                if (kmClass.isInnerClass()) {
                    into.add(kmClass.toInnerClassInfo())
                } else {
                    into.add(kmClass.toClassInfo())
                }
            }
            is KotlinClassMetadata.FileFacade -> {
                val kmPackage = metadata.toKmPackage()
                kmPackage.functions.forEach {
                    if (it.isVisibleFromOutside()) {
                        if (it.isExtensionMethod()) {
                            into.add(it.toExtensionFunction(nodeInfo.pkg))
                        } else {
                            into.add(it.toGlobalFunction(nodeInfo.pkg))
                        }
                    }
                }
            }
            else -> {
                //ignore?
            }
        }
    }

    private val skippedParts =
        ClassReader.SKIP_CODE.or(ClassReader.SKIP_DEBUG).or(ClassReader.SKIP_FRAMES)
}

private fun ClassNode.kotlinMetadataAnnotation(): KotlinClassMetadata? {
    return visibleAnnotations?.mapNotNull {
        it.kotlinMetadata()
    }?.firstOrNull() ?: invisibleAnnotations?.mapNotNull {
        it.kotlinMetadata()
    }?.firstOrNull()
}

private fun AnnotationNode.kotlinMetadata(): KotlinClassMetadata? {
    return extractKotlinMetadata()
}