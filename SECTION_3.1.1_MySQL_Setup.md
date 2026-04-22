# 3.1.1 Cài Đặt Cơ Sở Dữ Liệu MySQL

## Giới Thiệu

Cơ sở dữ liệu MySQL là hệ quản trị cơ sở dữ liệu mã nguồn mở được sử dụng rộng rãi trong các ứng dụng web. Để thiết lập môi trường phát triển cục bộ (local development) cho dự án WeConnect, chúng ta sẽ cài đặt MySQL Server trực tiếp trên máy tính cá nhân mà không cần Docker, kết hợp với công cụ quản lý Database DBeaver để dễ dàng quản lý cơ sở dữ liệu.

## Bước 1: Tải và Cài Đặt MySQL Server

### Tải MySQL Community Server

**Trên Windows/Mac/Linux:**

1. Truy cập trang chủ MySQL: https://www.mysql.com/downloads/
2. Chọn **MySQL Community Server** (bản miễn phí)
3. Tải phiên bản mới nhất (khuyên dùng MySQL 8.0+)

**Hình 3.1 - Trang Tải MySQL**
```
┌─────────────────────────────────────────────────────┐
│  MySQL Official Download Page                       │
│  https://www.mysql.com/downloads/                  │
│                                                     │
│  [MySQL Community Server] ← Chọn đây              │
│  [MySQL Enterprise Edition]                        │
│  [MySQL Cluster]                                    │
│  [MySQL Router]                                     │
│  [MySQL Workbench]                                 │
│  [MySQL Shell]                                      │
│  [Connectors]                                       │
└─────────────────────────────────────────────────────┘
```

### Cài Đặt MySQL Server

#### Trên Windows:

1. **Chạy Installer:**
   - Tải file `.msi` từ mysql.com
   - Nhấp đôi để chạy installer

2. **Chọn Setup Type:**
   ```
   [ ] Developer Default (khuyên dùng) - cài MySQL Server + Tools
   [ ] Server Only
   [ ] Full Setup
   [ ] Custom
   ```
   → Chọn **Developer Default**

3. **Server Configuration:**
   - Port: `3306` (mặc định)
   - MySQL Service: `MySQL80` (tên service)
   - Khởi động khi boot: `Yes` ✓

4. **Accounts and Roles:**
   - Root password: Nhập mật khẩu mạnh (ví dụ: `WeConnect@2026`)
   - MySQL User: `weconnect_user` / `weconnect_password` (tùy chọn)

5. **Kết Thúc Cài Đặt:**
   - MySQL Server khởi động tự động
   - Có thể kiểm tra trạng thái trong Services (Windows)

#### Trên Mac (Homebrew - Khuyên Dùng):

```bash
# Cài đặt MySQL qua Homebrew
brew install mysql

# Khởi động MySQL
brew services start mysql

# Đặt mật khẩu root
mysql_secure_installation

# Kiểm tra phiên bản
mysql --version
```

#### Trên Linux (Ubuntu/Debian):

```bash
# Update packages
sudo apt update

# Cài đặt MySQL Server
sudo apt install mysql-server

# Bảo mật setup (đặt mật khẩu root)
sudo mysql_secure_installation

# Khởi động MySQL service
sudo systemctl start mysql

# Kiểm tra trạng thái
sudo systemctl status mysql
```

## Bước 2: Tải và Cài Đặt DBeaver

### Giới Thiệu DBeaver

DBeaver là công cụ quản lý database mạnh mẽ hỗ trợ MySQL, PostgreSQL, SQLite, v.v. Giao diện GUI giúp dễ dàng:
- Tạo/xóa database và tables
- Chạy SQL queries
- Quản lý users và permissions
- Import/Export dữ liệu
- Debug và tối ưu hóa

### Tải DBeaver Community (Miễn Phí)

1. Truy cập: https://dbeaver.io/download/
2. Tải **DBeaver Community Edition** (free)
3. Chạy installer tương ứng hệ điều hành

**Hình 3.2 - Trang Tải DBeaver**
```
┌─────────────────────────────────────────────────────┐
│  DBeaver Official Download                          │
│  https://dbeaver.io/download/                       │
│                                                     │
│  [DBeaver Community Edition] ← Chọn đây            │
│  [DBeaver Enterprise]                               │
│  [DBeaver Server]                                   │
│  [Source Code]                                      │
└─────────────────────────────────────────────────────┘
```

## Bước 3: Kết Nối MySQL với DBeaver

### Khởi Động DBeaver

1. Mở ứng dụng DBeaver
2. Chọn **Database** → **New Database Connection**

### Cấu Hình Kết Nối

**Hình 3.3 - Dialog Tạo Kết Nối MySQL**
```
┌──────────────────────────────────────────────────┐
│  Create New Database Connection                  │
├──────────────────────────────────────────────────┤
│                                                  │
│  1. Chọn Database Type: MySQL ← Chọn            │
│     [SQLite] [PostgreSQL] [MySQL] [Oracle] ...   │
│                                                  │
│  2. Điền Thông Tin:                             │
│     Server Host:  localhost                      │
│     Port:         3306                           │
│     Database:     (để trống)                     │
│     Username:     root                           │
│     Password:     [nhập mật khẩu root]          │
│                                                  │
│  3. Test Connection                              │
│     [<] [>] [Test Connection] → Connected! ✓    │
│                                                  │
│  4. Finish                                       │
│     [Cancel]  [Next >]  [Finish]                │
└──────────────────────────────────────────────────┘
```

**Chi tiết cấu hình:**

| Trường | Giá Trị | Ghi Chú |
|-------|--------|--------|
| **Server Host** | `localhost` hoặc `127.0.0.1` | Máy tính cá nhân |
| **Port** | `3306` | Cổng MySQL mặc định |
| **Database** | (để trống) | Kết nối server trước, sau tạo DB |
| **Username** | `root` | Tài khoản admin |
| **Password** | `[mật khẩu root]` | Nhập khi cài MySQL |
| **Save password locally** | ✓ | Ghi nhớ mật khẩu |
| **Test Connection** | [Nút] | Kiểm tra kết nối |

### Tạo Database Mới

1. **Trong DBeaver**, kích phải chuột vào kết nối MySQL
2. Chọn **Create** → **Database**

**Hình 3.4 - Tạo Database weconnect_db**
```
┌──────────────────────────────────────────────────┐
│  Create New Database                              │
├──────────────────────────────────────────────────┤
│                                                  │
│  Database Name:  weconnect_db                    │
│                                                  │
│  Character Set:  utf8mb4 (khuyên dùng) ← Quan  |
│                  trọng cho tiếng Việt           |
│  Collation:      utf8mb4_unicode_ci              │
│                                                  │
│  [Cancel]  [OK]                                  │
└──────────────────────────────────────────────────┘
```

**Chọn Character Set `utf8mb4`** ← Rất quan trọng!
- Hỗ trợ đầy đủ Unicode
- Cho phép lưu trữ emoji, ký tự Việt
- Tương thích với tất cả ứng dụng

## Bước 4: Cài Đặt Backend Spring Boot

### Cấu Hình application.properties

Sau khi MySQL chạy, cập nhật file cấu hình backend:

**File:** `d:\Weconnect\backend\src\main\resources\application.properties`

```properties
# ========== MySQL Configuration ==========
# Kết nối tới database
spring.datasource.url=jdbc:mysql://localhost:3306/weconnect_db?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8mb4&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=WeConnect@2026
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.format_sql=true

# SQL Logging
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# ========== Server Configuration ==========
server.address=0.0.0.0
server.port=8081

# ========== JWT Configuration ==========
jwt.secret=WeConnectSecretKey2026SuperSecureRandomStringForJWTTokenGeneration123456
jwt.expiration=86400000

# ========== Logging ==========
logging.level.root=INFO
logging.level.com.weconnect.backend=DEBUG
```

### Giải Thích Các Tham Số

| Tham Số | Ý Nghĩa | Ví Dụ |
|---------|---------|-------|
| `spring.datasource.url` | Địa chỉ kết nối MySQL | `jdbc:mysql://localhost:3306/weconnect_db` |
| `?createDatabaseIfNotExist=true` | Tự tạo database nếu chưa có | Không cần tạo thủ công |
| `?useUnicode=true` | Hỗ trợ Unicode | Cho tiếng Việt |
| `?characterEncoding=utf8mb4` | Bảng mã UTF-8 | Hỗ trợ emoji, ký tự đặc biệt |
| `?serverTimezone=UTC` | Múi giờ server | Tránh lỗi timezone |
| `spring.datasource.username` | Tài khoản MySQL | `root` |
| `spring.datasource.password` | Mật khẩu MySQL | `[nhập mật khẩu]` |
| `spring.jpa.hibernate.ddl-auto` | Tự động tạo/update tables | `update` = update table nếu thay đổi |

### Dependency MySQL trong pom.xml

Kiểm tra Backend đã có dependency MySQL:

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

hoặc (phiên bản mới):

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.1.0</version>
</dependency>
```

## Bước 5: Giao Diện Quản Lý Database với DBeaver

### Các Tính Năng Chính

**Hình 3.5 - Giao Diện DBeaver Dashboard**
```
┌────────────────────────────────────────────────────────┐
│ DBeaver Desktop                            ▢ ▢ ✕       │
├────────────────────────────────────────────────────────┤
│                                                        │
│  Left Panel (Navigation):                             │
│  ├─ Database Connections                             │
│  │  ├─ MySQL - localhost:3306 [Connected ✓]         │
│  │  │  ├─ weconnect_db (Database)                    │
│  │  │  │  ├─ Tables                                  │
│  │  │  │  │  ├─ users                                │
│  │  │  │  │  ├─ posts                                │
│  │  │  │  │  ├─ post_members                         │
│  │  │  │  │  ├─ user_reviews                         │
│  │  │  │  │  ├─ reports                              │
│  │  │  │  │  ├─ friendships                          │
│  │  │  │  │  ├─ blocked_users                        │
│  │  │  │  │  ├─ chat_rooms                           │
│  │  │  │  │  ├─ chat_messages                        │
│  │  │  │  │  ├─ chat_room_members                    │
│  │  │  │  │  └─ notifications                        │
│  │  │  │  └─ Views                                   │
│  │  │  └─ Views                                       │
│  │  └─ [New Connection +]                             │
│  │                                                    │
│  Center Panel (SQL Editor & Data View):             │
│  ┌────────────────────────────────────────────────┐  │
│  │ SQL Scripts | weconnect_db | users | Results  │  │
│  ├────────────────────────────────────────────────┤  │
│  │ SELECT * FROM users;        [Run ▶]  [Exec F9] │  │
│  │                                                │  │
│  │ Results (10 rows):                            │  │
│  │ ┌──────────────────────────────────────────┐  │  │
│  │ │ id │ email │ fullName │ role │ createdAt │  │  │
│  │ ├──────────────────────────────────────────┤  │  │
│  │ │ 1  │admin@ │ Admin    │ 1    │ 2026-04-20│  │  │
│  │ │ 2  │user1@ │ Người Dùng 1 │ 0 │ 2026-04-20│  │  │
│  │ └──────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────┘  │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### Các Thao Tác Thường Dùng

#### 1. Xem Dữ Liệu Bảng
```
1. Kích đôi chuột vào table (ví dụ: users)
2. Tab "Data" hiển thị toàn bộ dữ liệu
3. Có thể sắp xếp, lọc, tìm kiếm
```

#### 2. Chạy SQL Query
```
1. Chuột phải → New SQL Script
2. Soạn SQL: SELECT * FROM users WHERE role = 1;
3. Nhấn F9 hoặc [Run ▶]
4. Xem kết quả trong tab Results
```

#### 3. Tạo Table Mới
```
1. Right click → Create → Table
2. Đặt tên table
3. Add Columns (tên, kiểu dữ liệu, nullable, primary key)
4. Save
```

#### 4. Thêm Dữ Liệu
```
1. Mở tab Data của table
2. Nút [+] Insert new row
3. Điền dữ liệu vào ô
4. Nhấn Ctrl+S để lưu
```

#### 5. Tối Ưu Hóa Queries
```
1. Chạy query thường
2. Tab Explain Plan → xem cách MySQL thực thi
3. DBeaver gợi ý tạo indexes để tăng tốc độ
```

## Bước 6: Khởi Động Và Kiểm Tra

### Khởi Động MySQL Service

**Trên Windows (Command Prompt/PowerShell):**
```bash
# Kiểm tra trạng thái service MySQL
sc query MySQL80

# Khởi động MySQL (nếu chưa chạy)
net start MySQL80

# Dừng MySQL
net stop MySQL80
```

**Trên Mac:**
```bash
# Kiểm tra trạng thái
brew services list

# Khởi động
brew services start mysql

# Dừng
brew services stop mysql
```

**Trên Linux:**
```bash
# Kiểm tra trạng thái
sudo systemctl status mysql

# Khởi động
sudo systemctl start mysql

# Dừng
sudo systemctl stop mysql
```

### Kiểm Tra Kết Nối Từ Command Line

```bash
# Kết nối tới MySQL server
mysql -h localhost -u root -p

# Nhập mật khẩu root
Enter password: [mật khẩu]

# Nếu thành công, sẽ thấy:
mysql> 

# Xem databases hiện có
mysql> SHOW DATABASES;

# Chọn database
USE weconnect_db;

# Xem tables
SHOW TABLES;

# Thoát
EXIT;
```

### Kiểm Tra Từ Backend (Spring Boot)

Khi khởi động Spring Boot Backend:
1. Mở IntelliJ IDEA
2. Chạy `WeconnectApplication` main class
3. Xem console log:

```
[INFO] Initializing Spring Data H2 repository support
[INFO] HikariPool-1 - Starting...
[INFO] HikariPool-1 - Start completed.
[INFO] Hibernate: create table users ...
[INFO] Hibernate: create table posts ...
[SUCCESS] Started WeconnectApplication in 5.234 seconds
```

Nếu thấy thông báo CREATE/UPDATE table → **Thành công!** ✓

## Workflow Quản Lý Database

```
┌──────────────────────────────────────┐
│  Quá Trình Phát Triển (Dev Cycle)    │
├──────────────────────────────────────┤
│                                      │
│  1. Thay đổi Entity (Java Class)    │
│  2. Khởi động Spring Boot Backend    │
│  3. Hibernate tự động update schema  │
│  4. Mở DBeaver xem thay đổi          │
│  5. Kiểm tra dữ liệu bằng SQL Query  │
│  6. Debug nếu có lỗi                 │
│                                      │
└──────────────────────────────────────┘
```

## Bảng Tóm Tắt Cài Đặt

| Bước | Tác Vụ | Công Cụ | Trạng Thái |
|------|--------|---------|-----------|
| 1 | Tải MySQL Server | mysql.com | ✓ Cài Đặt |
| 2 | Cài MySQL | Installer | ✓ Chạy (port 3306) |
| 3 | Tải DBeaver | dbeaver.io | ✓ Cài Đặt |
| 4 | Kết nối DBeaver → MySQL | DBeaver | ✓ Connected |
| 5 | Tạo database `weconnect_db` | DBeaver | ✓ Tạo Thành Công |
| 6 | Cấu hình Backend | application.properties | ✓ Config |
| 7 | Chạy Spring Boot Backend | IntelliJ | ✓ Tables Auto-Created |
| 8 | Kiểm tra dữ liệu | DBeaver SQL | ✓ Ready |

## Kết Luận

Bây giờ bạn đã có:
- ✅ MySQL Server chạy trên localhost:3306
- ✅ DBeaver GUI để quản lý database dễ dàng
- ✅ Database `weconnect_db` sẵn sàng
- ✅ Spring Boot Backend tự động tạo tables
- ✅ Môi trường phát triển cục bộ hoàn chỉnh

Bạn có thể bắt đầu phát triển ứng dụng WeConnect! 🚀
