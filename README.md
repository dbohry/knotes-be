# kNotes

A modern, efficient note-taking application with automatic compression, dark mode theming, and mobile-responsive design.

## Quick Start

### Prerequisites

- Java 25 or higher
- MongoDB

### Running the Application

1. **Build the application**
   ```bash
   ./gradlew build
   ```

2. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

3. **Access the application**
   - Open your browser to `http://localhost:8080`
   - Start typing to create your first note!

## API

### Note Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/notes/{id}` | Retrieve a note by ID |
| `POST` | `/api/notes` | Create a new note |
| `PUT` | `/api/notes` | Update an existing note |

### Request/Response Examples

**Create Note:**
```bash
POST /api/notes
Content-Type: application/json

{
  "note": "Your note content here"
}
```

**Response:**
```json
{
  "id": "01KDECFWYDMS857DZMCR680MCY",
  "content": "Your note content here",
  "createdAt": "2024-12-27T10:30:00Z",
  "modifiedAt": "2024-12-27T10:30:00Z"
}
```

## Testing

Run the test suite:
```bash
./gradlew test
```

Test categories:
- **Unit tests** for compression utilities
- **Integration tests** for Note entity
- **Service layer tests** for business logic

## Configuration

### MongoDB Configuration

The application uses MongoDB for storage. Configure connection in `application.properties`:

```properties
spring.data.mongodb.uri=mongodb://localhost:27017/knotes
```

---

## Live

https://notes.lhamacorp.com/01KDFNNWYG2MJR2TD9F0384T3N