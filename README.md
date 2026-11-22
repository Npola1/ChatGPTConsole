# ChatGPTConsole

A lightweight Java console client for the OpenAI Responses API. Chat from your terminal with follow-up questions in the same session. Uses only the Java standard library.

## Requirements

- Java 11 or newer
- An OpenAI API key with access to your chosen model

## Run

From the project folder, set your API key and start the program (macOS/Linux):

```sh
export OPENAI_API_KEY="your-api-key"
javac ChatGPTConsole.java
java ChatGPTConsole
```

Enter a prompt, or type `exit` or `quit` to close the program.

The default model is `gpt-5-mini`. Set `OPENAI_MODEL` to use another model.

Keep your API key private. Prompts are sent to OpenAI, and the program requests response storage to support conversation continuity.
