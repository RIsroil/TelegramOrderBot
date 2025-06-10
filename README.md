TelegramOrderBot

TelegramOrderBot is a multi-bot ordering system built using Java Spring Boot. 
It allows customers to place food orders via Telegram, admins to manage sales reports, and offers full tracking and control over order statuses (Ordered, Sold, Cancelled). 
The project includes three bots:

👥 ClientBot – for customer interactions and ordering.
📊 SalesReportBot – for admin reporting and analytics.
👮‍♂️ AdminBot – for future administrative operations (optional/extendable).

🚀 Features
🛍 ClientBot
• /start to register user and collect name + phone number.
• Interactive menu system with inline keyboards.
• Supports add to cart, edit basket, confirm order.
• Supports pickup or delivery options.
• Automatically blocks abusive users and notifies when block ends.
• Sends new orders to a Telegram group chat for kitchen processing.
• Supports basket clearing, order confirmation, and real-time updates.

📊 SalesReportBot
• Provides daily, weekly, and monthly sales reports.
• Allows report generation by custom dates.
• Lists clients with canceled orders.
• Admin can block/unblock users via inline buttons.
• Tracks users with most cancellations.
• Supports detailed order history lookup by phone number.

🛡 AdminBot
• Provides full access to manage users, menus, and orders.
• Can add, edit, or remove menu items with prices and availability.

🛠 Technologies Used
• Java 23
• Spring Boot
• TelegramBots Java Library
• JPA/Hibernate
• PostgreSQL (or your choice of DB)
• Lombok
• Maven

🗂 Project Structure
├── config/                 # Bot configs and initializer
├── model/                 # JPA entities (Client, Orders, Menu, etc.)
├── repository/            # Spring Data JPA repositories
├── service/               # Business logic for ClientBot, SalesReportBot, etc.
└── DemoApplication.java   # Spring Boot main application

⚙️ Setup Instructions
1. Clone the repository:
git clone https://github.com/RIsroil/TelegramOrderBot.git

2. Configure your bots in application.properties:
bot.username=YourAdminBotUsername
bot.token=YourAdminBotToken

client.bot.username=YourClientBotUsername
client.bot.token=YourClientBotToken

hisobot.bot.username=YourSalesReportBotUsername
hisobot.bot.token=YourSalesReportBotToken

3. Run the project:
mvn clean install
java -jar target/TelegramOrderBot.jar

📎 Notes
Make sure your bots are registered with BotFather and enabled for receiving messages.
The bot uses inline keyboards and group chat integration – configure the group chat ID in ClientBot.java.

📧 Contact
For questions, issues, or suggestions, reach out to the author:
📨 Telegram: @Rakhimov_Isroil

