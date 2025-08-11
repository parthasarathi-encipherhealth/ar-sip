# Asterisk IVR System for Encipher Health

This is a Spring Boot application that provides an IVR (Interactive Voice Response) system using Asterisk ARI (Asterisk REST Interface) instead of Twilio. The system is designed to handle automated AR (Accounts Receivable) calls for insurance companies.

## Features

- **Asterisk ARI Integration**: Connects to Asterisk server using ARI for call management
- **AI-Powered Responses**: Integrates with Azure OpenAI for intelligent call handling
- **Speech Recognition**: Uses Azure Speech Services for audio transcription
- **Text-to-Speech**: Converts AI responses to speech using Azure TTS
- **WebSocket Support**: Real-time communication for call monitoring
- **MongoDB Storage**: Stores call history and patient records
- **Sonetel Integration**: SIP provider integration for external calls
- **Comprehensive Logging**: Detailed logging for debugging and monitoring

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- Asterisk 18+ with ARI enabled
- MongoDB 4.4+
- Azure OpenAI API key
- Azure Speech Services API key
- Sonetel account (for SIP calls)

## Asterisk Configuration

### 1. Enable ARI in Asterisk

Add the following to your `ari.conf`:

```ini
[asterisk]
type = user
read_only = no
password = asterisk
```

### 2. Enable HTTP Server

Add to `http.conf`:

```ini
[general]
enabled = yes
bindaddr = 0.0.0.0
bindport = 8088
```

### 3. Configure ARI Application

Add to `extensions.conf`:

```ini
[from-internal]
exten => _X.,1,NoOp()
exten => _X.,n,Stasis(ar_app)
exten => _X.,n,Hangup()
```

### 4. Enable Recording

Ensure recording is enabled in your Asterisk configuration.

## Application Configuration

### 1. Update `application.properties`

```properties
# Asterisk Configuration
asterisk.host=your.asterisk.server.ip
asterisk.httpPort=8088
asterisk.ariUser=asterisk
asterisk.ariPassword=asterisk
asterisk.ariApp=ar_app

# Azure OpenAI
azure.openai.api.key=your_openai_api_key
azure.openai.api.url=https://api.openai.com/v1/chat/completions
azure.openai.model=gpt-4o-mini

# Azure Speech Services
azure.speech.key=your_azure_speech_key
azure.speech.region=your_azure_region

# Sonetel Configuration
sonetel.username=your_sonetel_username
sonetel.password=your_sonetel_password
sonetel.domain=your_sonetel_domain

# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/asterisk_ivr
```

### 2. Environment Variables

You can also set these as environment variables:

```bash
export ASTERISK_HOST=your.asterisk.server.ip
export AZURE_OPENAI_API_KEY=your_openai_api_key
export AZURE_SPEECH_KEY=your_azure_speech_key
```

## Building and Running

### 1. Build the Application

```bash
mvn clean package
```

### 2. Run the Application

```bash
java -jar target/sip-0.0.1-SNAPSHOT.jar
```

Or with Maven:

```bash
mvn spring-boot:run
```

## API Endpoints

### Call Management

- `GET /ar/connect?patientId={id}` - Initialize a new call
- `POST /ar/asterisk?patientId={id}` - Handle Asterisk actions
- `POST /ar/gather?patientId={id}` - Handle speech input
- `GET /ar/end/{callId}` - End a specific call
- `GET /ar/endAllLiveCalls` - End all live calls

### Call Information

- `GET /ar/getAllLiveCalls` - Get all live calls
- `GET /ar/getAllCallDetails` - Get all call details
- `GET /ar/callStatus?callId={id}` - Get call status
- `GET /ar/health` - Health check

### WebSocket

- `WS /ws` - WebSocket endpoint for real-time updates

## Call Flow

1. **Call Initiation**: Call is initiated via `/ar/connect` endpoint
2. **Asterisk Integration**: Asterisk receives the call and sends it to the ARI application
3. **Speech Recognition**: User's speech is recorded and transcribed
4. **AI Processing**: Azure OpenAI processes the transcript and generates a response
5. **Response Action**: The system either speaks text, plays DTMF tones, or ends the call
6. **Loop**: Process continues until end keywords are detected

## WebSocket Events

The WebSocket provides real-time updates for:

- Call status changes
- Speech input received
- AI responses generated
- Call end events

## Error Handling

The system includes comprehensive error handling:

- **Retry Logic**: Automatic retries for API calls
- **Logging**: Detailed logging for debugging
- **Graceful Degradation**: System continues to function even if some services fail
- **Exception Handling**: Proper exception handling and user feedback

## Monitoring and Logging

### Log Levels

- `DEBUG`: Detailed debugging information
- `INFO`: General information about system operations
- `WARN`: Warning messages for potential issues
- `ERROR`: Error messages with stack traces

### Key Metrics

- Call success/failure rates
- Response times for AI services
- WebSocket connection status
- Database operation performance

## Security Considerations

- **API Keys**: Store sensitive API keys in environment variables
- **Network Security**: Ensure Asterisk server is behind a firewall
- **Authentication**: Implement proper authentication for production use
- **HTTPS**: Use HTTPS in production environments

## Troubleshooting

### Common Issues

1. **WebSocket Connection Failed**
   - Check Asterisk ARI configuration
   - Verify network connectivity
   - Check firewall settings

2. **AI Service Not Responding**
   - Verify API keys are correct
   - Check Azure service status
   - Review rate limiting

3. **Recording Issues**
   - Check Asterisk recording configuration
   - Verify file permissions
   - Check disk space

### Debug Mode

Enable debug logging by setting:

```properties
logging.level.com.encipherhealth.sip=DEBUG
```

## Development

### Project Structure

```
src/main/java/com/encipherhealth/sip/
├── controller/          # REST API controllers
├── service/            # Business logic services
├── component/          # WebSocket and other components
├── entity/             # MongoDB entities
├── dto/                # Data transfer objects
├── enums/              # Enumeration classes
└── config/             # Configuration classes
```

### Adding New Features

1. Create new service classes in the `service` package
2. Add corresponding controllers in the `controller` package
3. Update entities and DTOs as needed
4. Add proper logging and error handling
5. Update tests and documentation

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

## Deployment

### Docker

```dockerfile
FROM openjdk:21-jdk-slim
COPY target/sip-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes

Create deployment and service manifests for Kubernetes deployment.

## Support

For issues and questions:

1. Check the logs for error messages
2. Review the configuration settings
3. Verify Asterisk and MongoDB connectivity
4. Check Azure service status

## License

This project is proprietary to Encipher Health.
