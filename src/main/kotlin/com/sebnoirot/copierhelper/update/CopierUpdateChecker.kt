package com.sebnoirot.copierhelper.update

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.sebnoirot.copierhelper.copier.GitVersionFetcher
import java.io.File
import java.nio.file.Paths

/**
 * Service that checks for Copier template updates.
 */
@Service(Service.Level.PROJECT)
class CopierUpdateChecker(private val project: Project) {
    private val LOG = Logger.getInstance(CopierUpdateChecker::class.java)

    companion object {
        const val ANSWERS_FILE = ".copier-answers.yml"
    }

    /**
     * Find the Copier answers file in the project.
     *
     * @return The answers file or null if not found
     */
    fun findAnswersFile(): File? {
        val basePath = project.basePath ?: return null
        val baseDir = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(basePath))
            ?: return null

        var answersFile: VirtualFile? = null

        // Look for .copier-answers.yml in the project root
        baseDir.refresh(false, false)
        answersFile = baseDir.findChild(ANSWERS_FILE)

        if (answersFile != null && answersFile.exists()) {
            LOG.info("Found Copier answers file: ${answersFile.path}")
            return File(answersFile.path)
        }

        return null
    }

    /**
     * Check if a newer template version is available.
     *
     * @return Triple of (source path, current version, newest version) or null if no update available
     */
    fun checkForUpdates(): Triple<String, String, String>? {
        val answersFile = findAnswersFile() ?: return null

        // Parse the answers file
        val answers = CopierAnswersParser.parseAnswers(answersFile)
        val sourcePath = CopierAnswersParser.extractSourcePath(answers) ?: return null
        val currentVersion = CopierAnswersParser.extractVersion(answers) ?: return null

        LOG.info("Checking for updates for template: $sourcePath, current version: $currentVersion")

        // First try to check using tags (preferred method)
        val tags = GitVersionFetcher.fetchTags(sourcePath)
        if (tags.isNotEmpty()) {
            LOG.info("Found ${tags.size} tags for template: $sourcePath")

            // Find the newest tag
            // This is a simple implementation that assumes tags are sortable
            // A more robust implementation would parse semantic versions
            val newestTag = tags.maxOrNull() ?: return null

            // Check if the newest tag is different from the current version
            if (newestTag != currentVersion && newestTag > currentVersion) {
                LOG.info("Update available: $newestTag (current: $currentVersion)")
                return Triple(sourcePath, currentVersion, newestTag)
            }

            LOG.info("No update available via tags. Current version: $currentVersion, newest tag: $newestTag")
            return null
        }

        // If no tags found, fall back to comparing commit hashes with the latest commit on main branch
        LOG.info("No tags found for template: $sourcePath, falling back to commit comparison")

        // Check if current version looks like a commit hash (40 or 7 characters of hex)
        val isCurrentVersionCommitHash = currentVersion.matches(Regex("^[0-9a-f]{7,40}$"))
        if (!isCurrentVersionCommitHash) {
            LOG.info("Current version doesn't look like a commit hash: $currentVersion")
            return null
        }

        // Fetch the latest commit from the main branch
        val latestCommit = GitVersionFetcher.fetchLatestCommit(sourcePath)
        if (latestCommit == null) {
            LOG.info("Failed to fetch latest commit for template: $sourcePath")
            return null
        }

        LOG.info("Comparing current commit: $currentVersion with latest commit: $latestCommit")

        // Check if the commits are different
        // We can't directly compare which is newer without cloning the repo,
        // so we just check if they're different
        if (latestCommit != currentVersion && !latestCommit.startsWith(currentVersion) && !currentVersion.startsWith(latestCommit)) {
            LOG.info("Update available: $latestCommit (current: $currentVersion)")
            return Triple(sourcePath, currentVersion, latestCommit)
        }

        LOG.info("No update available via commit comparison. Current commit: $currentVersion, latest commit: $latestCommit")
        return null
    }
}
