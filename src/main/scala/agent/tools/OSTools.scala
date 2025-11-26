package agent.tools

import agent.core.{Tool, ToolBase, ToolDataType, State}
import agent.core.ToolDataType.toolDataTypeToJsonSchema
import agent.core.ToolDataType.toolDataTypeToReadWriter

import upickle.default.*
import scala.util.Try
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets
import java.io.IOException

// ============================================================================
// OS Tools - File and Folder Operations
// ============================================================================

object OSTools:
  def allTools: List[ToolBase] =
    List(
      new CreateFileTool(),
      new CreateFolderTool(),
      new ListDirectoryTool(),
      new ReadFileTool(),
      new WriteFileTool(),
      new AppendFileTool(),
      new DeleteFileTool(),
      new FileExistsTool(),
      new MoveFileTool(),
      new CopyFileTool(),
    )

  def safeTools: List[ToolBase] =
    List(
      new ListDirectoryTool(),
      new ReadFileTool(),
      new FileExistsTool(),
    )

// ============================================================================
// Create File Tool
// ============================================================================

/**
 *  Input for creating a file.
 *
 *  @param path The path where the file should be created
 *  @param content The content to write to the file
 *  @param overwrite Whether to overwrite if the file already exists (default: false)
 */
case class CreateFileInput(
  path: String,
  content: String,
  overwrite: Boolean = false
) derives ToolDataType

/**
 *  Output from creating a file.
 *
 *  @param path The path of the created file
 *  @param created Whether the file was successfully created
 *  @param message Status message
 */
case class CreateFileOutput(
  path: String,
  created: Boolean,
  message: String
) derives ToolDataType

/**
 *  Tool for creating a new file with specified content.
 */
class CreateFileTool extends Tool[CreateFileInput, CreateFileOutput]:
  override val name: String = "create_file"

  override val description: String =
    "Create a new file at the specified path with the given content. Set overwrite=true to replace an existing file."

  override def invoke(input: CreateFileInput)(using state: State): Try[CreateFileOutput] =
    Try {
      val filePath = Paths.get(input.path).toAbsolutePath.normalize()

      if Files.exists(filePath) && !input.overwrite then
        CreateFileOutput(
          path = filePath.toString,
          created = false,
          message = s"File already exists. Set overwrite=true to replace it."
        )
      else
        // Create parent directories if they don't exist
        Option(filePath.getParent).foreach(Files.createDirectories(_))
        Files.writeString(filePath, input.content, StandardCharsets.UTF_8)
        CreateFileOutput(
          path = filePath.toString,
          created = true,
          message = "File created successfully"
        )
    }

// ============================================================================
// Create Folder Tool
// ============================================================================

/**
 *  Input for creating a folder.
 *
 *  @param path The path where the folder should be created
 *  @param createParents Whether to create parent directories if they don't exist (default: true)
 */
case class CreateFolderInput(
  path: String,
  createParents: Boolean = true
) derives ToolDataType

/**
 *  Output from creating a folder.
 *
 *  @param path The path of the created folder
 *  @param created Whether the folder was successfully created
 *  @param message Status message
 */
case class CreateFolderOutput(
  path: String,
  created: Boolean,
  message: String
) derives ToolDataType

/**
 *  Tool for creating a new directory.
 */
class CreateFolderTool extends Tool[CreateFolderInput, CreateFolderOutput]:
  override val name: String = "create_folder"

  override val description: String =
    "Create a new folder/directory at the specified path. By default, creates parent directories if they don't exist."

  override def invoke(input: CreateFolderInput)(using state: State): Try[CreateFolderOutput] =
    Try {
      val folderPath = Paths.get(input.path).toAbsolutePath.normalize()

      if Files.exists(folderPath) then
        if Files.isDirectory(folderPath) then
          CreateFolderOutput(
            path = folderPath.toString,
            created = false,
            message = "Folder already exists"
          )
        else
          CreateFolderOutput(
            path = folderPath.toString,
            created = false,
            message = "A file with this name already exists"
          )
      else
        if input.createParents then
          Files.createDirectories(folderPath)
        else
          Files.createDirectory(folderPath)

        CreateFolderOutput(
          path = folderPath.toString,
          created = true,
          message = "Folder created successfully"
        )
    }

// ============================================================================
// List Directory Tool (ls)
// ============================================================================

/**
 *  Input for listing directory contents.
 *
 *  @param path The path of the directory to list
 *  @param showHidden Whether to include hidden files (default: false)
 *  @param recursive Whether to list recursively (default: false)
 */
case class ListDirectoryInput(
  path: String,
  showHidden: Boolean = false,
  recursive: Boolean = false
) derives ToolDataType

/**
 *  Information about a file or directory entry.
 *
 *  @param name The name of the entry
 *  @param path The full path of the entry
 *  @param isDirectory Whether the entry is a directory
 *  @param size The size in bytes (0 for directories)
 */
case class DirectoryEntry(
  name: String,
  path: String,
  isDirectory: Boolean,
  size: Long
) derives ToolDataType

/**
 *  Output from listing a directory.
 *
 *  @param path The path that was listed
 *  @param entries List of entries in the directory
 *  @param count Total number of entries
 */
case class ListDirectoryOutput(
  path: String,
  entries: List[DirectoryEntry],
  count: Int
) derives ToolDataType

/**
 *  Tool for listing the contents of a directory.
 */
class ListDirectoryTool extends Tool[ListDirectoryInput, ListDirectoryOutput]:
  override val name: String = "list_directory"

  override val description: String =
    "List the contents of a directory. Similar to 'ls' command. Can show hidden files and list recursively."

  override def invoke(input: ListDirectoryInput)(using state: State): Try[ListDirectoryOutput] =
    Try {
      val dirPath = Paths.get(input.path).toAbsolutePath.normalize()

      if !Files.exists(dirPath) then
        throw new IOException(s"Path does not exist: $dirPath")

      if !Files.isDirectory(dirPath) then
        throw new IOException(s"Path is not a directory: $dirPath")

      import scala.jdk.CollectionConverters.*

      val entries = if input.recursive then
        Files.walk(dirPath).iterator().asScala.toList.tail // tail to skip the root directory itself
      else
        Files.list(dirPath).iterator().asScala.toList

      val filteredEntries = entries
        .filter(p => input.showHidden || !p.getFileName.toString.startsWith("."))
        .map { p =>
          DirectoryEntry(
            name = p.getFileName.toString,
            path = p.toString,
            isDirectory = Files.isDirectory(p),
            size = if Files.isDirectory(p) then 0L else Files.size(p)
          )
        }
        .sortBy(e => (!e.isDirectory, e.name.toLowerCase)) // directories first, then alphabetical

      ListDirectoryOutput(
        path = dirPath.toString,
        entries = filteredEntries,
        count = filteredEntries.size
      )
    }

// ============================================================================
// Read File Tool
// ============================================================================

/**
 *  Input for reading a file.
 *
 *  @param path The path of the file to read
 *  @param maxLines Maximum number of lines to read (optional, reads all if not specified)
 *  @param startLine Line number to start reading from (1-indexed, default: 1)
 */
case class ReadFileInput(
  path: String,
  maxLines: Option[Int] = None,
  startLine: Int = 1
) derives ToolDataType

/**
 *  Output from reading a file.
 *
 *  @param path The path of the file that was read
 *  @param content The content of the file
 *  @param totalLines Total number of lines in the file
 *  @param linesRead Number of lines actually read
 */
case class ReadFileOutput(
  path: String,
  content: String,
  totalLines: Int,
  linesRead: Int
) derives ToolDataType

/**
 *  Tool for reading the contents of a file.
 */
class ReadFileTool extends Tool[ReadFileInput, ReadFileOutput]:
  override val name: String = "read_file"

  override val description: String =
    "Read the contents of a file. Can read specific line ranges using startLine and maxLines parameters."

  override def invoke(input: ReadFileInput)(using state: State): Try[ReadFileOutput] =
    Try {
      val filePath = Paths.get(input.path).toAbsolutePath.normalize()

      if !Files.exists(filePath) then
        throw new IOException(s"File does not exist: $filePath")

      if Files.isDirectory(filePath) then
        throw new IOException(s"Path is a directory, not a file: $filePath")

      val allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8)
      import scala.jdk.CollectionConverters.*
      val linesList = allLines.asScala.toList
      val totalLines = linesList.size

      val startIdx = math.max(0, input.startLine - 1)
      val selectedLines = input.maxLines match
        case Some(max) => linesList.slice(startIdx, startIdx + max)
        case None => linesList.drop(startIdx)

      ReadFileOutput(
        path = filePath.toString,
        content = selectedLines.mkString("\n"),
        totalLines = totalLines,
        linesRead = selectedLines.size
      )
    }

// ============================================================================
// Write File Tool (overwrite)
// ============================================================================

/**
 *  Input for writing to a file.
 *
 *  @param path The path of the file to write
 *  @param content The content to write
 */
case class WriteFileInput(
  path: String,
  content: String
) derives ToolDataType

/**
 *  Output from writing to a file.
 *
 *  @param path The path of the file that was written
 *  @param bytesWritten Number of bytes written
 *  @param message Status message
 */
case class WriteFileOutput(
  path: String,
  bytesWritten: Long,
  message: String
) derives ToolDataType

/**
 *  Tool for writing content to a file (overwrites existing content).
 */
class WriteFileTool extends Tool[WriteFileInput, WriteFileOutput]:
  override val name: String = "write_file"

  override val description: String =
    "Write content to a file, overwriting any existing content. Creates the file if it doesn't exist."

  override def invoke(input: WriteFileInput)(using state: State): Try[WriteFileOutput] =
    Try {
      val filePath = Paths.get(input.path).toAbsolutePath.normalize()

      // Create parent directories if they don't exist
      Option(filePath.getParent).foreach(Files.createDirectories(_))

      val bytes = input.content.getBytes(StandardCharsets.UTF_8)
      Files.write(filePath, bytes)

      WriteFileOutput(
        path = filePath.toString,
        bytesWritten = bytes.length,
        message = "File written successfully"
      )
    }

// ============================================================================
// Append File Tool
// ============================================================================

/**
 *  Input for appending to a file.
 *
 *  @param path The path of the file to append to
 *  @param content The content to append
 */
case class AppendFileInput(
  path: String,
  content: String
) derives ToolDataType

/**
 *  Output from appending to a file.
 *
 *  @param path The path of the file that was appended to
 *  @param bytesAppended Number of bytes appended
 *  @param message Status message
 */
case class AppendFileOutput(
  path: String,
  bytesAppended: Long,
  message: String
) derives ToolDataType

/**
 *  Tool for appending content to a file.
 */
class AppendFileTool extends Tool[AppendFileInput, AppendFileOutput]:
  override val name: String = "append_file"

  override val description: String =
    "Append content to the end of a file. Creates the file if it doesn't exist."

  override def invoke(input: AppendFileInput)(using state: State): Try[AppendFileOutput] =
    Try {
      val filePath = Paths.get(input.path).toAbsolutePath.normalize()

      // Create parent directories if they don't exist
      Option(filePath.getParent).foreach(Files.createDirectories(_))

      val bytes = input.content.getBytes(StandardCharsets.UTF_8)
      Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)

      AppendFileOutput(
        path = filePath.toString,
        bytesAppended = bytes.length,
        message = "Content appended successfully"
      )
    }

// ============================================================================
// Delete File Tool
// ============================================================================

/**
 *  Input for deleting a file or folder.
 *
 *  @param path The path of the file or folder to delete
 *  @param recursive Whether to recursively delete folder contents (default: false)
 */
case class DeleteFileInput(
  path: String,
  recursive: Boolean = false
) derives ToolDataType

/**
 *  Output from deleting a file or folder.
 *
 *  @param path The path that was deleted
 *  @param deleted Whether the deletion was successful
 *  @param message Status message
 */
case class DeleteFileOutput(
  path: String,
  deleted: Boolean,
  message: String
) derives ToolDataType

/**
 *  Tool for deleting a file or folder.
 */
class DeleteFileTool extends Tool[DeleteFileInput, DeleteFileOutput]:
  override val name: String = "delete_file"

  override val description: String =
    "Delete a file or folder. Use recursive=true to delete non-empty folders."

  override def invoke(input: DeleteFileInput)(using state: State): Try[DeleteFileOutput] =
    Try {
      val targetPath = Paths.get(input.path).toAbsolutePath.normalize()

      if !Files.exists(targetPath) then
        DeleteFileOutput(
          path = targetPath.toString,
          deleted = false,
          message = "Path does not exist"
        )
      else if Files.isDirectory(targetPath) then
        import scala.jdk.CollectionConverters.*
        val hasContents = Files.list(targetPath).iterator().hasNext

        if hasContents && !input.recursive then
          DeleteFileOutput(
            path = targetPath.toString,
            deleted = false,
            message = "Directory is not empty. Set recursive=true to delete."
          )
        else
          if input.recursive then
            // Delete recursively
            Files.walk(targetPath)
              .sorted(java.util.Comparator.reverseOrder())
              .forEach(Files.delete(_))
          else
            Files.delete(targetPath)

          DeleteFileOutput(
            path = targetPath.toString,
            deleted = true,
            message = "Directory deleted successfully"
          )
      else
        Files.delete(targetPath)
        DeleteFileOutput(
          path = targetPath.toString,
          deleted = true,
          message = "File deleted successfully"
        )
    }

// ============================================================================
// File Exists Tool
// ============================================================================

/**
 *  Input for checking if a file exists.
 *
 *  @param path The path to check
 */
case class FileExistsInput(
  path: String
) derives ToolDataType

/**
 *  Output from checking if a file exists.
 *
 *  @param path The path that was checked
 *  @param exists Whether the path exists
 *  @param isFile Whether the path is a file
 *  @param isDirectory Whether the path is a directory
 *  @param size Size in bytes (if it's a file)
 */
case class FileExistsOutput(
  path: String,
  exists: Boolean,
  isFile: Boolean,
  isDirectory: Boolean,
  size: Long
) derives ToolDataType

/**
 *  Tool for checking if a file or directory exists.
 */
class FileExistsTool extends Tool[FileExistsInput, FileExistsOutput]:
  override val name: String = "file_exists"

  override val description: String =
    "Check if a file or directory exists at the specified path. Returns information about the path type and size."

  override def invoke(input: FileExistsInput)(using state: State): Try[FileExistsOutput] =
    Try {
      val targetPath = Paths.get(input.path).toAbsolutePath.normalize()
      val exists = Files.exists(targetPath)
      val isFile = exists && Files.isRegularFile(targetPath)
      val isDirectory = exists && Files.isDirectory(targetPath)
      val size = if isFile then Files.size(targetPath) else 0L

      FileExistsOutput(
        path = targetPath.toString,
        exists = exists,
        isFile = isFile,
        isDirectory = isDirectory,
        size = size
      )
    }

// ============================================================================
// Move File Tool
// ============================================================================

/**
 *  Input for moving/renaming a file or folder.
 *
 *  @param source The source path
 *  @param destination The destination path
 *  @param overwrite Whether to overwrite if destination exists (default: false)
 */
case class MoveFileInput(
  source: String,
  destination: String,
  overwrite: Boolean = false
) derives ToolDataType

/**
 *  Output from moving a file or folder.
 *
 *  @param source The source path
 *  @param destination The destination path
 *  @param moved Whether the move was successful
 *  @param message Status message
 */
case class MoveFileOutput(
  source: String,
  destination: String,
  moved: Boolean,
  message: String
) derives ToolDataType

/**
 *  Tool for moving or renaming a file or folder.
 */
class MoveFileTool extends Tool[MoveFileInput, MoveFileOutput]:
  override val name: String = "move_file"

  override val description: String =
    "Move or rename a file or folder. Can also be used to rename files."

  override def invoke(input: MoveFileInput)(using state: State): Try[MoveFileOutput] =
    Try {
      val sourcePath = Paths.get(input.source).toAbsolutePath.normalize()
      val destPath = Paths.get(input.destination).toAbsolutePath.normalize()

      if !Files.exists(sourcePath) then
        MoveFileOutput(
          source = sourcePath.toString,
          destination = destPath.toString,
          moved = false,
          message = "Source path does not exist"
        )
      else if Files.exists(destPath) && !input.overwrite then
        MoveFileOutput(
          source = sourcePath.toString,
          destination = destPath.toString,
          moved = false,
          message = "Destination already exists. Set overwrite=true to replace."
        )
      else
        // Create parent directories if they don't exist
        Option(destPath.getParent).foreach(Files.createDirectories(_))

        import java.nio.file.StandardCopyOption
        val options = if input.overwrite then
          Array(StandardCopyOption.REPLACE_EXISTING)
        else
          Array.empty[StandardCopyOption]

        Files.move(sourcePath, destPath, options*)

        MoveFileOutput(
          source = sourcePath.toString,
          destination = destPath.toString,
          moved = true,
          message = "Moved successfully"
        )
    }

// ============================================================================
// Copy File Tool
// ============================================================================

/**
 *  Input for copying a file or folder.
 *
 *  @param source The source path
 *  @param destination The destination path
 *  @param overwrite Whether to overwrite if destination exists (default: false)
 */
case class CopyFileInput(
  source: String,
  destination: String,
  overwrite: Boolean = false
) derives ToolDataType

/**
 *  Output from copying a file or folder.
 *
 *  @param source The source path
 *  @param destination The destination path
 *  @param copied Whether the copy was successful
 *  @param message Status message
 */
case class CopyFileOutput(
  source: String,
  destination: String,
  copied: Boolean,
  message: String
) derives ToolDataType

/**
 *  Tool for copying a file or folder.
 */
class CopyFileTool extends Tool[CopyFileInput, CopyFileOutput]:
  override val name: String = "copy_file"

  override val description: String =
    "Copy a file to a new location. For directories, copies all contents recursively."

  override def invoke(input: CopyFileInput)(using state: State): Try[CopyFileOutput] =
    Try {
      val sourcePath = Paths.get(input.source).toAbsolutePath.normalize()
      val destPath = Paths.get(input.destination).toAbsolutePath.normalize()

      if !Files.exists(sourcePath) then
        CopyFileOutput(
          source = sourcePath.toString,
          destination = destPath.toString,
          copied = false,
          message = "Source path does not exist"
        )
      else if Files.exists(destPath) && !input.overwrite then
        CopyFileOutput(
          source = sourcePath.toString,
          destination = destPath.toString,
          copied = false,
          message = "Destination already exists. Set overwrite=true to replace."
        )
      else
        // Create parent directories if they don't exist
        Option(destPath.getParent).foreach(Files.createDirectories(_))

        import java.nio.file.StandardCopyOption
        val options = if input.overwrite then
          Array(StandardCopyOption.REPLACE_EXISTING)
        else
          Array.empty[StandardCopyOption]

        if Files.isDirectory(sourcePath) then
          // Copy directory recursively
          import scala.jdk.CollectionConverters.*
          Files.walk(sourcePath).forEach { source =>
            val dest = destPath.resolve(sourcePath.relativize(source))
            if Files.isDirectory(source) then
              Files.createDirectories(dest)
            else
              Files.copy(source, dest, options*)
          }
        else
          Files.copy(sourcePath, destPath, options*)

        CopyFileOutput(
          source = sourcePath.toString,
          destination = destPath.toString,
          copied = true,
          message = "Copied successfully"
        )
    }
