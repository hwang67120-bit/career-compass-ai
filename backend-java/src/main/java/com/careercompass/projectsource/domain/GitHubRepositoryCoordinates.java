package com.careercompass.projectsource.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;

public record GitHubRepositoryCoordinates(
        String owner,
        String repository,
        URI canonicalUrl
) {

    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "github.com";
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    public static GitHubRepositoryCoordinates createFromUrl(String repositoryUrl) {
        URI uri = createUri(repositoryUrl);
        validateUriBoundary(uri);

        String path = normalizePath(uri.getRawPath());
        String[] pathSegments = path.split("/", -1);
        if (pathSegments.length != 2) {
            throw new InvalidGitHubRepositoryUrlException();
        }

        String owner = pathSegments[0];
        String repository = removeGitSuffix(pathSegments[1]);
        validatePathSegment(owner);
        validatePathSegment(repository);

        URI canonicalUrl = URI.create(ALLOWED_SCHEME + "://" + ALLOWED_HOST
                + "/" + owner + "/" + repository);
        return new GitHubRepositoryCoordinates(owner, repository, canonicalUrl);
    }

    public String fullName() {
        return owner + "/" + repository;
    }

    private static URI createUri(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new InvalidGitHubRepositoryUrlException();
        }

        try {
            return new URI(repositoryUrl.strip());
        } catch (URISyntaxException exception) {
            throw new InvalidGitHubRepositoryUrlException(exception);
        }
    }

    private static void validateUriBoundary(URI uri) {
        boolean hasAllowedScheme = ALLOWED_SCHEME.equalsIgnoreCase(uri.getScheme());
        boolean hasAllowedHost = uri.getHost() != null
                && ALLOWED_HOST.equals(uri.getHost().toLowerCase(Locale.ROOT));
        boolean hasUnexpectedComponent = uri.getUserInfo() != null
                || uri.getPort() != -1
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null;

        if (!hasAllowedScheme || !hasAllowedHost || hasUnexpectedComponent) {
            throw new InvalidGitHubRepositoryUrlException();
        }
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || rawPath.contains("%")) {
            throw new InvalidGitHubRepositoryUrlException();
        }

        String path = rawPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String removeGitSuffix(String repository) {
        if (repository.endsWith(".git") && repository.length() > 4) {
            return repository.substring(0, repository.length() - 4);
        }
        return repository;
    }

    private static void validatePathSegment(String pathSegment) {
        if (pathSegment.isBlank()
                || ".".equals(pathSegment)
                || "..".equals(pathSegment)
                || !SAFE_PATH_SEGMENT.matcher(pathSegment).matches()) {
            throw new InvalidGitHubRepositoryUrlException();
        }
    }
}
