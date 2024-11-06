// 获取用户名和密码
var username = prompt("请输入用户名：");
var password = prompt("请输入密码：");

if (!username || !password) {
    alert("用户名和密码不能为空！");
    throw new Error("用户名和密码不能为空");
}

// 建立 WebSocket 连接
var ws = new WebSocket("ws://localhost:8081/chat");

ws.onopen = function () {
    // 连接建立后，发送登录消息
    var loginMessage = {
        type: "login",
        username: username,
        password: password
    };
    ws.send(JSON.stringify(loginMessage));
};

ws.onmessage = function (event) {
    console.log("Received data:", event.data); // 添加日志
    var data = JSON.parse(event.data);
    if (data.type === "login") {
        if (data.status === "success") {
            console.log("登录成功！");
            // 可以在此初始化聊天界面
        } else {
            alert("登录失败：" + data.message);
            ws.close();
        }
    } else if (data.type === "message") {
        console.log("Message received:", data); // 添加日志
        // 接收服务器发送的消息
        displayMessage(data);
    } else if (data.type === "error") {
        // 处理错误消息
        alert("错误：" + data.message);
    }
};

ws.onclose = function () {
    console.log("连接已关闭");
};

ws.onerror = function (error) {
    console.error("WebSocket 错误：", error);
};

// 发送消息
var sendButton = document.getElementById("sendButton");
var messageInput = document.getElementById("messageInput");
var receiverInput = document.getElementById("receiverInput");

sendButton.addEventListener("click", function () {
    var content = messageInput.value.trim();
    var receiverInputValue = receiverInput.value.trim();
    var receiver = receiverInputValue ? receiverInputValue : null; // 如果没有指定接收者，设置为 null

    if (content) {
        var message = {
            type: "message",
            content: content,
            sender: username,
            receiver: receiver,
            timestamp: new Date().getTime() // 使用当前时间的时间戳
        };

        // 立即显示消息
        displayMessage(message);

        ws.send(JSON.stringify(message));
        messageInput.value = "";
    }
});

// 按下回车键发送消息
messageInput.addEventListener("keypress", function (event) {
    if (event.key === "Enter") {
        sendButton.click();
    }
});

function displayMessage(data) {
    console.log("Display message:", data); // 添加日志
    var messagesList = document.getElementById("messages");
    var newMessage = document.createElement("li");
    var sender = data.sender;
    var content = data.content;
    var receiver = data.receiver;
    var timestamp = data.timestamp; // 获取时间戳

    // 检查 timestamp 是否有效
    if (typeof timestamp !== 'number' || isNaN(timestamp)) {
        console.error("Invalid timestamp:", timestamp);
        return;
    }

    // 将时间戳转换为日期对象
    var date = dayjs(timestamp);
    console.log("Parsed date:", date.format('YYYY-MM-DD HH:mm:ss')); // 添加日志

    // 格式化日期时间字符串，例如 "2024-11-06 17:00:00"
    var formattedTime = date.format('YYYY-MM-DD HH:mm:ss');

    var messageText = "";

    if (receiver && (receiver === username || sender === username)) {
        // **私聊消息**
        if (sender === username) {
            // 自己发送的私聊消息
            messageText = "[" + formattedTime + "] 你对 " + receiver + " 悄悄说: " + content;
        } else {
            // 别人发送给你的私聊消息
            messageText = "[" + formattedTime + "] " + sender + " 对你悄悄说: " + content;
        }
    } else {
        // **群聊消息**
        messageText = "[" + formattedTime + "] " + sender + ": " + content;
    }
    newMessage.textContent = messageText;
    messagesList.appendChild(newMessage);
}
