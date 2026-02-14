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

### Workflow

When making code changes:

1. ✅ Make your changes
2. ✅ Run applicable style checks (server-style for Java, client-style for TypeScript)
3. ✅ Fix any violations
4. ✅ Re-run checks to confirm they pass
5. ✅ Only then commit and push

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

Both style checks run automatically in GitHub Actions:
- **server-style**: Runs `./gradlew checkstyleMain -x webapp`
- **client-style**: Runs formatting and linting checks

Pull requests cannot be merged if either check fails.

## Best Practices

1. **Run checks locally** before pushing to avoid CI failures
2. **Fix style issues immediately** - don't accumulate technical debt
3. **Understand the violations** - don't just blindly fix them
4. **Keep documentation up to date** - Javadoc and comments should match the code

---

*This file ensures that all agents (human or AI) maintain consistent code quality standards across the Harmonia project.*
