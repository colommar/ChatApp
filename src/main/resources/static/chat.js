// 获取登录和注册相关元素
var loginContainer = document.getElementById("loginContainer");
var registerContainer = document.getElementById("registerContainer");
var showRegister = document.getElementById("showRegister");
var showLogin = document.getElementById("showLogin");
var loginForm = document.getElementById("loginForm");
var registerForm = document.getElementById("registerForm");
var container = document.getElementById("container");

var ws; // WebSocket 对象
var username; // 当前登录的用户名

// 切换到注册界面
showRegister.addEventListener("click", function (event) {
    event.preventDefault();
    loginContainer.classList.remove("active");
    registerContainer.classList.add("active");
});

// 切换到登录界面
showLogin.addEventListener("click", function (event) {
    event.preventDefault();
    registerContainer.classList.remove("active");
    loginContainer.classList.add("active");
});

// 处理登录表单提交
loginForm.addEventListener("submit", function (event) {
    event.preventDefault();
    var loginUsername = document.getElementById("loginUsername").value.trim();
    var loginPassword = document.getElementById("loginPassword").value.trim();

    if (!loginUsername || !loginPassword) {
        alert("用户名和密码不能为空！");
        return;
    }

    // 建立 WebSocket 连接
    ws = new WebSocket("ws://localhost:8081/chat");

    ws.onopen = function () {
        console.log("WebSocket 连接已打开");
        // 连接建立后，发送登录消息
        var loginMessage = {
            type: "login",
            username: loginUsername,
            password: loginPassword
        };
        ws.send(JSON.stringify(loginMessage));
    };

    ws.onmessage = function (event) {
        console.log("Received data:", event.data); // 调试日志
        var data;
        try {
            data = JSON.parse(event.data);
        } catch (e) {
            console.error("JSON 解析错误:", e);
            return;
        }

        if (data.type === "login") {
            handleLoginResponse(data);
        } else if (data.type === "register") {
            handleRegisterResponse(data);
        } else if (data.type === "message") {
            displayMessage(data);
        } else if (data.type === "userList") {
            updateUserList(data.users);
        } else if (data.type === "userStatusUpdate") {
            updateUserStatus(data.users);
        } else if (data.type === "error") {
            alert("错误：" + data.message);
        } else if (data.type === "history") {
            loadChatHistory(data.messages);
        }
    };

    ws.onclose = function () {
        console.log("WebSocket 连接已关闭");
        alert("WebSocket 连接已关闭。");
        // 重置界面
        container.style.display = "none";
        loginContainer.classList.add("active");
        document.getElementById("currentUsername").textContent = "当前用户: 未登录";
    };

    ws.onerror = function (error) {
        console.error("WebSocket 错误：", error);
        alert("WebSocket 连接错误。");
    };
});

// 处理注册表单提交
registerForm.addEventListener("submit", function (event) {
    event.preventDefault();
    var registerUsername = document.getElementById("registerUsername").value.trim();
    var registerPassword = document.getElementById("registerPassword").value.trim();

    if (!registerUsername || !registerPassword) {
        alert("用户名和密码不能为空！");
        return;
    }

    // 建立 WebSocket 连接
    ws = new WebSocket("ws://localhost:8081/chat");

    ws.onopen = function () {
        console.log("WebSocket 连接已打开");
        // 连接建立后，发送注册消息
        var registerMessage = {
            type: "register",
            username: registerUsername,
            password: registerPassword
        };
        ws.send(JSON.stringify(registerMessage));
    };

    ws.onmessage = function (event) {
        console.log("Received data:", event.data); // 调试日志
        var data;
        try {
            data = JSON.parse(event.data);
        } catch (e) {
            console.error("JSON 解析错误:", e);
            return;
        }

        if (data.type === "register") {
            handleRegisterResponse(data);
        } else if (data.type === "userList") {
            updateUserList(data.users);
        } else if (data.type === "userStatusUpdate") {
            updateUserStatus(data.users);
        } else if (data.type === "error") {
            alert("错误：" + data.message);
        }
    };

    ws.onclose = function () {
        console.log("WebSocket 连接已关闭");
        alert("WebSocket 连接已关闭。");
        // 重置界面
        registerContainer.classList.remove("active");
        loginContainer.classList.add("active");
    };

    ws.onerror = function (error) {
        console.error("WebSocket 错误：", error);
        alert("WebSocket 连接错误。");
    };
});

// 发送消息
var sendButton = document.getElementById("sendButton");
var messageInput = document.getElementById("messageInput");
var receiverSelect = document.getElementById("receiverSelect");

sendButton.addEventListener("click", function () {
    var content = messageInput.value.trim();
    var receiverValue = receiverSelect.value;
    var receiver = receiverValue ? receiverValue : null; // 如果没有选择接收者，设置为 null

    if (content) {
        var message = {
            type: "message",
            content: content,
            sender: username,
            receiver: receiver,
            timestamp: new Date().getTime() // 使用当前时间的时间戳
        };

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

/**
 * 处理登录响应
 */
function handleLoginResponse(data) {
    if (data.status === "success") {
        console.log("登录成功！");
        loginContainer.classList.remove("active");
        container.style.display = "flex";
        username = document.getElementById("loginUsername").value.trim(); // 设置为全局变量
        // 更新当前用户名显示
        document.getElementById("currentUsername").textContent = "当前用户: " + username;
    } else {
        alert("登录失败：" + data.message);
        ws.close();
    }
}

/**
 * 处理注册响应
 */
function handleRegisterResponse(data) {
    if (data.status === "success") {
        alert("注册成功，请登录！");
        registerContainer.classList.remove("active");
        loginContainer.classList.add("active");
    } else {
        alert("注册失败：" + data.message);
    }
}

/**
 * 显示消息
 */
function displayMessage(data) {
    console.log("Display message:", data); // 调试日志
    var messagesList = document.getElementById("messages");
    var newMessage = document.createElement("div"); // 使用 div 而非 li
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
    console.log("Parsed date:", date.format('YYYY-MM-DD HH:mm')); // 调试日志

    // 格式化日期时间字符串，例如 "2024-11-06 09:00"
    var formattedTime = date.format('YYYY-MM-DD HH:mm');

    var messageHtml = "";

    if (receiver && (receiver === username || sender === username)) {
        // 私聊消息
        if (sender === username) {
            // 自己发送的私聊消息
            newMessage.className = "message private sent";
            messageHtml = `<div class="message-content">
                <div class="message-text">${content}</div>
                <div class="message-info">你悄悄对 <strong>${receiver}</strong> 说 • ${formattedTime}</div>
            </div>`;
        } else {
            // 别人发送给你的私聊消息
            newMessage.className = "message private received";
            messageHtml = `<div class="message-content">
                <div class="message-text">${content}</div>
                <div class="message-info"><strong>${sender}</strong> 悄悄对你说 • ${formattedTime}</div>
            </div>`;
        }
    } else {
        if (sender === username) {
            // 自己发送的群聊消息
            newMessage.className = "message group sent";
            messageHtml = `<div class="message-content">
                <div class="message-text">${content}</div>
                <div class="message-info">你 • ${formattedTime}</div>
            </div>`;
        } else {
            // 别人发送的群聊消息
            newMessage.className = "message group received";
            messageHtml = `<div class="message-content">
                <div class="message-text">${content}</div>
                <div class="message-info"><strong>${sender}</strong> • ${formattedTime}</div>
            </div>`;
        }
    }
    newMessage.innerHTML = messageHtml;
    messagesList.appendChild(newMessage);
    // 滚动到底部
    messagesList.scrollTop = messagesList.scrollHeight;
}

/**
 * 更新用户列表显示
 * @param {Object} users - 用户名与状态的映射
 */
function updateUserList(users) {
    console.log("Updating user list with users:", users); // 调试日志
    var userList = document.getElementById("userListItems");
    userList.innerHTML = ""; // 清空当前列表

    for (var user in users) {
        if (user === username) continue; // 不显示自己

        var status = users[user];

        var userItem = document.createElement("li");

        // 创建状态图标
        var statusIcon = document.createElement("span");
        statusIcon.classList.add("status-icon");
        if (status === "online") {
            statusIcon.classList.add("status-online");
        } else {
            statusIcon.classList.add("status-offline");
        }

        userItem.appendChild(statusIcon);

        // 用户名文本
        var userNameText = document.createElement("span");
        userNameText.textContent = user;
        userItem.appendChild(userNameText);

        // 添加点击事件，点击用户名自动选择接收者
        userNameText.addEventListener("click", function () {
            receiverSelect.value = this.textContent;
            console.log("Selected receiver:", this.textContent); // 调试日志
        });

        userList.appendChild(userItem);
    }

    // 更新接收者下拉框
    populateReceiverSelect(users);
}

/**
 * 更新所有用户的状态
 * @param {Object} users - 用户名与状态的映射
 */
function updateUserStatus(users) {
    console.log("Updating user status with users:", users); // 调试日志
    // 直接调用 updateUserList，因为 userStatusUpdate 包含完整用户列表
    updateUserList(users);
}

/**
 * 填充接收者下拉框
 * @param {Object} users - 用户名与状态的映射
 */
function populateReceiverSelect(users) {
    var receiverSelect = document.getElementById("receiverSelect");
    receiverSelect.innerHTML = '<option value="">选择接收者（可选）</option>'; // 重置选项

    for (var user in users) {
        if (user === username) continue; // 不显示自己

        var status = users[user];

        var option = document.createElement("option");
        option.value = user;
        // 显示用户名及其在线状态
        option.textContent = user + (status === "online" ? " (在线)" : " (离线)");
        receiverSelect.appendChild(option);
    }
}

/**
 * 加载聊天历史
 * @param {Array} messages - 聊天消息数组
 */
function loadChatHistory(messages) {
    console.log("Loading chat history:", messages); // 调试日志
    var messagesList = document.getElementById("messages");
    messagesList.innerHTML = ""; // 清空当前消息列表

    messages.forEach(function (message) {
        displayMessage(message);
    });
}
