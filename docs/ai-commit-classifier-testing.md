# Commit Classifier Testing Guide

## Overview
The commit classifier uses OpenAI GPT-4o-mini to classify commits into categories: FEATURE, BUG_FIX, TEST, REFACTOR, or TRIVIAL.

## Test Endpoint
```
GET /api/ai/classify-commit
```

### Parameters
- `sha` (optional): Commit SHA (default: "abc123")
- `message` (optional): Commit message (default: "Add user authentication")
- `files` (optional): Comma-separated file paths (default: "src/auth/AuthController.java,src/auth/AuthService.java")
- `diff` (optional): Actual code changes to prevent gaming

## Test Cases

### 1. FEATURE - Real Authentication
```
http://localhost:8080/api/ai/classify-commit?message=Add%20user%20authentication&diff=public%20class%20AuthService%20%7B%0A%20%20%20%20public%20boolean%20authenticate(String%20username,%20String%20password)%20%7B%0A%20%20%20%20%20%20%20%20return%20userRepository.findByUsername(username)%0A%20%20%20%20%20%20%20%20%20%20%20%20.map(user%20-%3E%20passwordEncoder.matches(password,%20user.getPassword()))%0A%20%20%20%20%20%20%20%20%20%20%20%20.orElse(false);%0A%20%20%20%20%7D%0A%7D
```
**Expected**: FEATURE with high confidence

### 2. BUG_FIX - Null Pointer Fix
```
http://localhost:8080/api/ai/classify-commit?message=Fix%20null%20pointer%20exception&diff=if%20(user%20!=%20null%20%26%26%20user.getName()%20!=%20null)%20%7B%0A%20%20%20%20return%20user.getName();%0A%7D%0Areturn%20%22Unknown%22;
```
**Expected**: BUG_FIX with high confidence

### 3. TEST - Unit Tests
```
http://localhost:8080/api/ai/classify-commit?message=Add%20tests%20for%20login&files=LoginTest.java&diff=@Test%0Apublic%20void%20testLoginSuccess()%20%7B%0A%20%20%20%20assertTrue(authService.login(%22user%22,%20%22pass%22));%0A%7D
```
**Expected**: TEST with high confidence

### 4. REFACTOR - Code Cleanup
```
http://localhost:8080/api/ai/classify-commit?message=Refactor%20user%20service&diff=private%20User%20findUser(String%20id)%20%7B%0A%20%20%20%20return%20userRepository.findById(id).orElseThrow();%0A%7D%0A%0Apublic%20User%20getUser(String%20id)%20%7B%0A%20%20%20%20return%20findUser(id);%0A%7D
```
**Expected**: REFACTOR with high confidence

### 5. TRIVIAL - Formatting
```
http://localhost:8080/api/ai/classify-commit?message=Format%20code&diff=-%20%20%20%20public%20void%20test()%7B%0A+%20%20%20%20public%20void%20test()%20%7B
```
**Expected**: TRIVIAL with high confidence

### 6. Gaming Detection - Misleading Message
```
http://localhost:8080/api/ai/classify-commit?message=Add%20user%20authentication&diff=System.out.println("random%20stuff");
```
**Expected**: TRIVIAL with high confidence (detects mismatch between message and code)

### 7. Gaming Detection - TODO Comment
```
http://localhost:8080/api/ai/classify-commit?message=Implement%20complex%20algorithm&diff=//%20TODO:%20implement%20later
```
**Expected**: TRIVIAL with low confidence

## Response Format
```json
{
  "label": "FEATURE",
  "confidence": 0.95,
  "reasoning": "Brief explanation of classification"
}
```

## Configuration
Located in `src/main/resources/config/application.yml`:
```yaml
harmonia:
  ai:
    enabled: true
    commit-classifier:
      enabled: true
      confidence-threshold: 0.7  # Below this = TRIVIAL
```
