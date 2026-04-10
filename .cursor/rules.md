Project follows this structure:

src/main/java/com/example/demo trong demo1
│
├── config/             
├── controller/         
├── service/            
│   ├── impl/           
│   └── strategy/   {chỉ dùng khi có payment }    
├── repository/         
├── entity/             
├── dto/                
├── exception/          
└── util

SOLID
ISP: tách interface nhỏ, đúng mục đích
DIP: dùng DI, không new trực tiếp
Design Pattern
Strategy: thay thế if-else, dùng cho logic nhiều nhánh (payment, auth...)
DRY
dùng util để tái sử dụng
tránh lặp code
base class cho entity/dto
KISS
chia nhỏ service
tên rõ nghĩa
tránh logic phức tạp
Clean Code
comment phần quan trọng
đặt tên dễ hiểu
không hard-code → constants
dùng logging, không print