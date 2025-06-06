package com.sebnoirot.copierhelper.copier

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * Fetches available versions (tags and branches) from a Git repository.
 * Also provides utility methods for Git operations.
 */
object GitVersionFetcher {
    private val LOG = Logger.getInstance(GitVersionFetcher::class.java)

    // Common branch names to check in order of preference
    private val MAIN_BRANCH_NAMES = listOf("main", "master", "trunk", "develop")

    /**
     * Fetch available tags from a Git repository.
     *
     * @param repoUrl The URL of the Git repository
     * @param indicator Optional progress indicator
     * @return List of tag names
     */
    fun fetchTags(repoUrl: String, indicator: ProgressIndicator? = null): List<String> {
        indicator?.text = "Fetching tags..."
        try {
            // Wait for the result from fetchRefs and then process it
            val refs = fetchRefs(repoUrl, "--tags", indicator).get()
            return refs.filter { it.contains("refs/tags/") }
                .map { it.substringAfterLast("refs/tags/") }
                .filter { !it.endsWith("^{}") } // Filter out annotated tag objects
        } catch (e: Exception) {
            LOG.error("Error fetching tags from $repoUrl", e)
            return emptyList()
        }
    }

    /**
     * Fetch the latest commit hash from the main branch of a Git repository.
     * Tries common branch names (main, master, trunk, develop) in order.
     *
     * @param repoUrl The URL of the Git repository
     * @param indicator Optional progress indicator
     * @return The latest commit hash or null if not found
     */
    fun fetchLatestCommit(repoUrl: String, indicator: ProgressIndicator? = null): String? {
        indicator?.text = "Fetching latest commit..."
        LOG.info("Fetching latest commit from $repoUrl")

        // First get all branches to find which main branch exists
        val branches = fetchBranches(repoUrl, indicator)

        // Try to find a main branch from our list of common names
        val mainBranch = MAIN_BRANCH_NAMES.firstOrNull { branchName -> 
            branches.contains(branchName) 
        } ?: branches.firstOrNull() // Fallback to first branch if none of the common ones exist

        if (mainBranch == null) {
            LOG.warn("No branches found in repository: $repoUrl")
            return null
        }

        LOG.info("Using branch '$mainBranch' as main branch")

        // Use CompletableFuture to get the result from the background thread
        val future = CompletableFuture<String?>()

        // Execute git command in a background thread to avoid ReadAction issues
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Fetch the specific reference for this branch
                val commandLine = GeneralCommandLine()
                    .withExePath("git")
                    .withParameters("ls-remote", repoUrl, "refs/heads/$mainBranch")
                    .withCharset(StandardCharsets.UTF_8)

                LOG.info("Command line: ${commandLine.commandLineString}")

                val processHandler = CapturingProcessHandler(commandLine)
                val output = processHandler.runProcess(30_000) // 30 seconds timeout

                if (output.exitCode != 0) {
                    LOG.warn("Git ls-remote failed: ${output.stderr}")
                    future.complete(null)
                    return@executeOnPooledThread
                }

                // Parse the output to get the commit hash
                val commitLine = output.stdout.lines()
                    .filter { it.isNotBlank() }
                    .firstOrNull()

                if (commitLine == null) {
                    LOG.warn("No commit found for branch: $mainBranch")
                    future.complete(null)
                    return@executeOnPooledThread
                }

                // Extract the commit hash (first part before tab)
                val commitHash = commitLine.split("\\s+".toRegex(), 2).firstOrNull()

                LOG.info("Latest commit on branch '$mainBranch': $commitHash")
                future.complete(commitHash)
            } catch (e: Exception) {
                LOG.error("Error fetching latest commit from $repoUrl", e)
                future.completeExceptionally(e)
            }
        }

        try {
            // Wait for the result from the background thread
            return future.get()
        } catch (e: Exception) {
            LOG.error("Error getting result from future", e)
            return null
        }
    }

    /**
     * Fetch available branches from a Git repository.
     *
     * @param repoUrl The URL of the Git repository
     * @param indicator Optional progress indicator
     * @return List of branch names
     */
    fun fetchBranches(repoUrl: String, indicator: ProgressIndicator? = null): List<String> {
        indicator?.text = "Fetching branches..."
        try {
            // Wait for the result from fetchRefs and then process it
            val refs = fetchRefs(repoUrl, "--heads", indicator).get()
            return refs.filter { it.contains("refs/heads/") }
                .map { it.substringAfterLast("refs/heads/") }
        } catch (e: Exception) {
            LOG.error("Error fetching branches from $repoUrl", e)
            return emptyList()
        }
    }

    /**
     * Fetch all available versions (tags and branches) from a Git repository.
     *
     * @param repoUrl The URL of the Git repository
     * @param indicator Optional progress indicator
     * @return Map of version type to list of version names
     */
    fun fetchVersions(repoUrl: String, indicator: ProgressIndicator? = null): Map<String, List<String>> {
        val tags = fetchTags(repoUrl, indicator)
        val branches = fetchBranches(repoUrl, indicator)

        return mapOf(
            "Tags" to tags,
            "Branches" to branches
        )
    }

    /**
     * Fetch refs from a Git repository.
     *
     * @param repoUrl The URL of the Git repository
     * @param refType The type of refs to fetch (--tags or --heads)
     * @param indicator Optional progress indicator
     * @return CompletableFuture with List of ref lines
     */
    private fun fetchRefs(repoUrl: String, refType: String, indicator: ProgressIndicator? = null): CompletableFuture<List<String>> {
        LOG.info("Fetching $refType from $repoUrl")

        val future = CompletableFuture<List<String>>()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val commandLine = GeneralCommandLine()
                    .withExePath("git")
                    .withParameters("ls-remote", refType, repoUrl)
                    .withCharset(StandardCharsets.UTF_8)

                LOG.info("Command line: ${commandLine.commandLineString}")

                val processHandler = CapturingProcessHandler(commandLine)
                val output = processHandler.runProcess(30_000) // 30 seconds timeout

                if (output.exitCode != 0) {
                    LOG.warn("Git ls-remote failed: ${output.stderr}")
                    future.complete(emptyList())
                } else {
                    val result = output.stdout.lines()
                        .filter { it.isNotBlank() }
                        .map { it.trim() }
                        .map { it.split("\\s+".toRegex(), 2).getOrNull(1) ?: "" }
                        .filter { it.isNotBlank() }
                    future.complete(result)
                }
            } catch (e: Exception) {
                LOG.error("Error fetching refs from $repoUrl", e)
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Check if a Git repository has uncommitted changes.
     *
     * @param directory The directory of the Git repository
     * @return True if the repository has uncommitted changes, false otherwise
     */
    fun hasUncommittedChanges(directory: String): Boolean {
        LOG.info("Checking for uncommitted changes in $directory")

        // Use CompletableFuture to get the result from the background thread
        val future = CompletableFuture<Boolean>()

        // Execute git command in a background thread to avoid ReadAction issues
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val commandLine = GeneralCommandLine()
                    .withExePath("git")
                    .withParameters("status", "--porcelain")
                    .withWorkDirectory(directory)
                    .withCharset(StandardCharsets.UTF_8)

                LOG.info("Command line: ${commandLine.commandLineString}")

                val processHandler = CapturingProcessHandler(commandLine)
                val output = processHandler.runProcess(10_000) // 10 seconds timeout

                if (output.exitCode != 0) {
                    LOG.warn("Git status failed: ${output.stderr}")
                    // If the command fails, we assume there are no uncommitted changes
                    future.complete(false)
                    return@executeOnPooledThread
                }

                // If the output is not empty, there are uncommitted changes
                val hasChanges = output.stdout.trim().isNotEmpty()
                LOG.info("Repository has uncommitted changes: $hasChanges")
                future.complete(hasChanges)
            } catch (e: Exception) {
                LOG.error("Error checking for uncommitted changes in $directory", e)
                future.complete(false) // Assume no changes on error
            }
        }

        try {
            // Wait for the result from the background thread
            return future.get()
        } catch (e: Exception) {
            LOG.error("Error getting result from future", e)
            return false // Assume no changes on error
        }
    }
}
