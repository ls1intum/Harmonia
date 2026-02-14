# Agent Guidelines for Harmonia

## Code Style Validation

**CRITICAL**: Before completing any code changes, agents MUST verify that both server and client style checks pass.

### Required Style Checks

1. **Server-style (Java/Checkstyle)**
   ```bash
   ./gradlew checkstyleMain checkstyleTest
   ```
   - Validates Java code style
   - Ensures Javadoc completeness (all public methods must have `@return` tags with descriptions)
   - Must pass before committing

2. **Client-style (TypeScript/ESLint/Prettier)**
   ```bash
   cd src/main/webapp
   npm run prettier:check
   npm run lint
   npx tsc --noEmit
   ```
   - Validates TypeScript code style
   - Ensures proper formatting
   - Type-checks the entire codebase
   - Must pass before committing

## Testing

**CRITICAL**: After making changes, run relevant tests to ensure functionality is not broken.

### Running Tests

1. **Server tests (Java/JUnit)**
   ```bash
   ./gradlew test
   ```
   - Runs all unit and integration tests
   - Must pass before committing
   - To run specific tests: `./gradlew test --tests ClassName`

2. **Client tests (TypeScript/Jest)** (if applicable)
   ```bash
   cd src/main/webapp
   npm test
   ```
   - Runs all client-side tests
   - Must pass before committing

## Pull Request Title Validation

**CRITICAL**: When creating pull requests, the title MUST follow the required format or the PR will fail CI validation.

### Required Format

PR titles must match this pattern:
```
`Category`: Description starting with capital letter
```

**Valid categories:**
- `Usability` - UI/UX improvements, user-facing changes
- `Performance` - Optimizations, speed improvements
- `Development` - New features, refactoring, technical changes
- `General` - Documentation, configuration, miscellaneous

**Examples:**
- ✅ `Development`: Adjust CQI weights to improve fairness calculation
- ✅ `Performance`: Optimize database queries for large teams
- ✅ `Usability`: Add loading spinner for better user feedback
- ✅ `General`: Update API documentation and examples
- ❌ `docs: update readme` (wrong format - missing backticks and capital letter)
- ❌ `Development: fix bug` (wrong - description must start with capital)

### Validation Check

Before pushing or after creating a PR, verify the title format:
```bash
gh pr view --json title
```

The `validate-pr-title` CI check will automatically verify the format.

### Workflow

When making code changes:

1. ✅ Make your changes
2. ✅ Run applicable style checks (server-style for Java, client-style for TypeScript)
3. ✅ Run relevant tests to verify functionality
4. ✅ Fix any violations or test failures
5. ✅ Re-run checks and tests to confirm they pass
6. ✅ Commit and push changes
7. ✅ **When creating a PR**: Ensure the title follows the required format (see PR Title Validation)
8. ✅ **After creating PR**: Verify `validate-pr-title` check passes with `gh pr checks`

### Common Style Issues

#### Java (Checkstyle)
- Missing or incomplete Javadoc comments
- Missing `@return` tags on methods that return values
- Missing `@param` tags on method parameters
- Incorrect indentation or formatting

#### TypeScript (ESLint/Prettier)
- Unused imports or variables
- Inconsistent formatting
- Type errors
- Missing semicolons or trailing commas

### CI/CD Integration

The following checks run automatically in GitHub Actions:
- **validate-pr-title**: Validates PR title format (must match required pattern)
- **server-style**: Runs `./gradlew checkstyleMain -x webapp`
- **client-style**: Runs formatting and linting checks
- **tests**: Runs `./gradlew test` to ensure all tests pass

Pull requests cannot be merged if any check fails.

## Best Practices

1. **Run checks and tests locally** before pushing to avoid CI failures
2. **Fix style issues and test failures immediately** - don't accumulate technical debt
3. **Understand the violations** - don't just blindly fix them
4. **Write tests for new functionality** - ensure code is properly tested
5. **Keep documentation up to date** - Javadoc and comments should match the code

---

*This file ensures that all agents (human or AI) maintain consistent code quality standards across the Harmonia project.*
