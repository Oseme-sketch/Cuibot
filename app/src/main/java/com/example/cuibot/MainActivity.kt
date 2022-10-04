package com.example.cuibot

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.cuibot.adapter.ChatAdapter
import com.example.cuibot.model.ChatMessage
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.cx.v3beta1.*
import io.grpc.CompositeChannelCredentials
import io.grpc.Grpc
import io.grpc.TlsChannelCredentials
import io.grpc.auth.MoreCallCredentials
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatView: RecyclerView
    private lateinit var messageText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var sessionName: String
    private lateinit var stub: SessionsGrpcKt.SessionsCoroutineStub

    private val chatMessageList: MutableList<ChatMessage> = mutableListOf(
        ChatMessage(
            "Hello! My name is Cuibot.\n\n" +
                    "I can provide basic info on conversational UI.\n\n" +
                    "What can I help you with?",
            "",
            listOf(),
            1)
    )
    private val sessionId: String = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatAdapter = ChatAdapter { position, target ->
            if (target == 10) {
                openWebPage(chatMessageList[position].link)
            } else {
                run {
                    addMessageToList(chatMessageList[position].actions[target], "", listOf(), 0)
                    sendMessageToBot(chatMessageList[position].actions[target])
                }
            }
        }

        chatView = findViewById(R.id.chat_view)
        chatView.adapter = chatAdapter
        chatView.setHasFixedSize(true)
        chatAdapter.submitList(chatMessageList)

        messageText = findViewById(R.id.message_text)
        sendButton = findViewById(R.id.send_button)
        sendButton.setOnClickListener {
            val message: String = messageText.text.toString()
            if (message.isNotEmpty()) {
                addMessageToList(message, "", listOf(), 0)
                sendMessageToBot(message)
            } else {
                Toast.makeText(
                    this@MainActivity, "Please enter message!", Toast.LENGTH_SHORT
                ).show()
            }
        }

        setupBot()
    }

    private fun openWebPage(url: String) {
        val webpage: Uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun addMessageToList(
        message: String,
        link: String,
        actions: List<String>,
        isReceived: Int
    ) {
        chatMessageList.add(ChatMessage(message, link, actions, isReceived))
        messageText.setText("")
        chatAdapter.submitList(chatMessageList)
        chatView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun sendMessageToBot(message: String) {
        val textInput = TextInput.newBuilder().setText(message)
        val queryInput =
            QueryInput.newBuilder().setText(textInput).setLanguageCode("en-US").build()
        lifecycleScope.launch {
            sendMessageInBg(queryInput)
        }
    }

    private suspend fun sendMessageInBg(
        queryInput: QueryInput
    ) {
        withContext(Default) {
            try {
                val request: DetectIntentRequest = DetectIntentRequest.newBuilder()
                    .setSession(sessionName)
                    .setQueryInput(queryInput)
                    .build()
                val response: DetectIntentResponse = stub.detectIntent(request)
                runOnUiThread {
                    updateUI(response)
                }
            } catch (e: java.lang.Exception) {
                Log.d(TAG, "sendMessageInBg: " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun updateUI(response: DetectIntentResponse) {
        var botReply: String
        var receivedFrom: Int
        var webUrl = ""
        val actionList: MutableList<String> = mutableListOf()

        if (response.queryResult.responseMessagesList[0].hasText()) {
            botReply = response.queryResult.responseMessagesList[0].text.textList[0]
                .toString()
            receivedFrom = 1
            addMessageToList(botReply, webUrl, actionList, receivedFrom)
        }

        if (response.queryResult.responseMessagesList[0].hasPayload()) {
            val descriptionList: MutableList<String> = mutableListOf()
            val firstMessageList = response.queryResult.responseMessagesList[0].payload
                .getFieldsOrThrow("richContent").listValue
                .getValuesOrBuilder(0).listValue
            val firstMessageCount = firstMessageList.valuesCount
            
            for (i in 0 until firstMessageCount) {
                val firstMessageStruct = firstMessageList.getValuesOrBuilder(i).structValue
                if (firstMessageStruct.containsFields("type")) {
                    if (firstMessageStruct.getFieldsOrThrow("type")
                            .stringValue == "description"
                    ){
                        val descriptionString = firstMessageStruct
                            .getFieldsOrThrow("text").listValue
                            .getValuesOrBuilder(0).stringValue
                        descriptionList.add(descriptionString)
                    }
                    
                    if (firstMessageStruct.getFieldsOrThrow("type")
                            .stringValue == "chips"
                    ){
                        val optionsList = firstMessageStruct
                            .getFieldsOrThrow("options").listValue
                        val optionsCount = optionsList.valuesCount
                        for (j in 0 until optionsCount) {
                            val optionsStruct = optionsList.getValuesOrBuilder(j).structValue
                            val optionString = optionsStruct
                                .getFieldsOrThrow("text").stringValue
                            actionList.add(optionString)
                        }
                    }
                    
                    if (firstMessageStruct.getFieldsOrThrow("type")
                            .stringValue == "button"
                    ){
                        val linkString = firstMessageStruct
                            .getFieldsOrThrow("link").stringValue
                        webUrl = linkString
                    }
                }
            }
            
            botReply = descriptionList.joinToString(separator = " ")
            receivedFrom = 2
            addMessageToList(botReply, webUrl, actionList, receivedFrom)
        }
    }

    private fun setupBot() {
        try {
            val details = applicationContext.resources.openRawResource(
                applicationContext.resources.getIdentifier(
                    "detail",
                    "raw",
                    applicationContext.packageName
                )
            ).bufferedReader().use { it.readText() }
            val detailsString = JSONObject(details)
            val projectId = detailsString.getString("projectId")
            val locationId = detailsString.getString("locationId")
            val agentId = detailsString.getString("agentId")
            val sessionNameComponent = listOf(
                "projects", projectId,
                "locations", locationId,
                "agents", agentId,
                "sessions", sessionId)
            sessionName = sessionNameComponent.joinToString(separator = "/")

            val stream = this.resources.openRawResource(R.raw.credential)
            val googleCredentials: GoogleCredentials = GoogleCredentials.fromStream(stream)
                .createScoped("https://www.googleapis.com/auth/dialogflow")
            val credentials = CompositeChannelCredentials.create(
                TlsChannelCredentials.create(),
                MoreCallCredentials.from(googleCredentials)
            )
            val channel = Grpc.newChannelBuilder(
                "dialogflow.googleapis.com:443",
                credentials
            ).build()
            stub = SessionsGrpcKt.SessionsCoroutineStub(channel)

            Log.d(TAG, "setupBot: Setup successful!")
        }catch (e: Exception) {
            Log.d(TAG, "setupBot: " + e.message)
        }
    }
}
