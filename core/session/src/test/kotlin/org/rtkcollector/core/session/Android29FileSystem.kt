package org.rtkcollector.core.session

import java.io.OutputStream
import java.net.URI
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.nio.file.spi.FileSystemProvider

/** Models the old Android provider, not the host JDK's uninterruptible stream factory. */
internal fun android29Path(path: Path): Path = Android29Path(path, Android29FileSystem(path.fileSystem))

private class Android29Path(val target: Path, private val fs: FileSystem) : Path by target {
    override fun getFileSystem() = fs
    override fun toFile(): java.io.File = target.toFile()
    override fun resolve(other: String): Path = Android29Path(target.resolve(other), fs)
    override fun resolve(other: Path): Path = Android29Path(target.resolve(unwrap(other)), fs)
    override fun toAbsolutePath(): Path = Android29Path(target.toAbsolutePath(), fs)
    override fun normalize(): Path = Android29Path(target.normalize(), fs)
    override fun getParent(): Path? = target.parent?.let { Android29Path(it, fs) }
}

private fun unwrap(path: Path): Path = (path as? Android29Path)?.target ?: path

private class Android29FileSystem(private val target: FileSystem) : FileSystem() {
    private val legacyProvider = Android29Provider(this, target.provider())
    override fun provider(): FileSystemProvider = legacyProvider
    override fun close() = Unit
    override fun isOpen() = true
    override fun isReadOnly() = target.isReadOnly
    override fun getSeparator(): String = target.separator
    override fun getRootDirectories(): Iterable<Path> = target.rootDirectories
    override fun getFileStores(): Iterable<FileStore> = target.fileStores
    override fun supportedFileAttributeViews(): Set<String> = target.supportedFileAttributeViews()
    override fun getPath(first: String, vararg more: String): Path = Android29Path(target.getPath(first, *more), this)
    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher = target.getPathMatcher(syntaxAndPattern)
    override fun getUserPrincipalLookupService(): UserPrincipalLookupService = target.userPrincipalLookupService
    override fun newWatchService(): WatchService = target.newWatchService()
}

private class Android29Provider(private val fs: FileSystem, private val target: FileSystemProvider) : FileSystemProvider() {
    override fun getScheme() = "android29-test"
    override fun newFileSystem(uri: URI, env: MutableMap<String, *>): FileSystem = fs
    override fun getFileSystem(uri: URI): FileSystem = fs
    override fun getPath(uri: URI): Path = fs.getPath(uri.path)
    override fun newOutputStream(path: Path, vararg options: OpenOption): OutputStream {
        val actual = if (options.isEmpty()) setOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE) else options.toSet() + StandardOpenOption.WRITE
        return Channels.newOutputStream(newByteChannel(path, actual.toMutableSet()))
    }
    override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>, vararg attrs: FileAttribute<*>): SeekableByteChannel =
        target.newByteChannel(unwrap(path), options, *attrs)
    override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>): DirectoryStream<Path> =
        target.newDirectoryStream(unwrap(dir), filter)
    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) = target.createDirectory(unwrap(dir), *attrs)
    override fun delete(path: Path) = target.delete(unwrap(path))
    override fun copy(source: Path, dest: Path, vararg options: CopyOption) = target.copy(unwrap(source), unwrap(dest), *options)
    override fun move(source: Path, dest: Path, vararg options: CopyOption) = target.move(unwrap(source), unwrap(dest), *options)
    override fun isSameFile(first: Path, second: Path) = target.isSameFile(unwrap(first), unwrap(second))
    override fun isHidden(path: Path) = target.isHidden(unwrap(path))
    override fun getFileStore(path: Path): FileStore = target.getFileStore(unwrap(path))
    override fun checkAccess(path: Path, vararg modes: AccessMode) = target.checkAccess(unwrap(path), *modes)
    override fun <V : FileAttributeView> getFileAttributeView(path: Path, type: Class<V>, vararg options: LinkOption): V? =
        target.getFileAttributeView(unwrap(path), type, *options)
    override fun <A : BasicFileAttributes> readAttributes(path: Path, type: Class<A>, vararg options: LinkOption): A =
        target.readAttributes(unwrap(path), type, *options)
    override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption): MutableMap<String, Any> =
        target.readAttributes(unwrap(path), attributes, *options)
    override fun setAttribute(path: Path, attribute: String, value: Any, vararg options: LinkOption) =
        target.setAttribute(unwrap(path), attribute, value, *options)
}
