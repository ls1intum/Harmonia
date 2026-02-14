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

### Workflow

When making code changes:

1. ✅ Make your changes
2. ✅ Run applicable style checks (server-style for Java, client-style for TypeScript)
3. ✅ Run relevant tests to verify functionality
4. ✅ Fix any violations or test failures
5. ✅ Re-run checks and tests to confirm they pass
6. ✅ Only then commit and push

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

Style checks and tests run automatically in GitHub Actions:
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
