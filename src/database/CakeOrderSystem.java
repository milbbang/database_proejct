package database;

import java.sql.*;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.time.LocalDate;

public class CakeOrderSystem {
	// 초기 인덱스 값 세팅
	static int orders_index = 11;
	static int customer_index = 11;
	static int orderitem_index = 17;

	public static void 초기화면() {
		System.out.println("============================");
		System.out.printf("\t케이크 주문 시스템\n");
		System.out.println("============================");
	}

	public static void 메뉴(Connection conn) {
		Scanner scanner = new Scanner(System.in);

		// addOrder에서 주문을 계속하지 않음을 선택했을 때 다시 메뉴 선택창으로 돌아오도록 while문으로 감쌌습니다.
		while (true) {
			System.out.println();
			System.out.println("1. 케이크 주문하기");
			System.out.println("2. 전체 주문 목록 조회");
			System.out.println("3. 현재 주문 내역 조회");
			System.out.println("4. 주문 수정");
			System.out.println("5. 고객 정보 수정");
			System.out.println("0. 프로그램 종료");

			int choice = -1;
			while (true) {
				try {
					System.out.print("[메뉴를 선택하세요] : ");
					choice = Integer.parseInt(scanner.nextLine());
					break;
				} catch (NumberFormatException e) {
					System.out.println("[오류] 숫자를 입력해주세요.\n");
					continue;
				}
			}

			switch (choice) {
			case 1: // 케이크 주문하기
				addOrder(conn);
				break;
			case 2: // 전체 주문 목록 조회
				System.out.print("이름을 입력해주세요: ");
				String name = scanner.nextLine();
				showCustomerOrder(conn, name);
				break;
			case 3: // 현재 주문 내역 상세
				System.out.print("주문ID 입력(ex)O001): ");
				String orderId = scanner.nextLine();
				showOrderDetail(conn, orderId);
				break;
			case 4: // 주문 수정
				updateOrder(conn);
				break;
			case 5: // 고객 정보 수정
				updateCustomer(conn);
				break;
			case 0:
				return;
			default:
				System.out.println("잘못된 선택입니다. 다시 선택해주세요.");
			}
		}
	}

	// case 1 : 케이크 주문하기
	private static void addOrder(Connection conn) {
		Scanner sc = new Scanner(System.in);
		// 고객 정보 추가
		System.out.print("이름을 입력하세요: ");
		String name = sc.nextLine();
		String customerID = findCustomerID(conn, name); // customerID 불러오기
		// orders 테이블 정보 추가
		String ordersID = "O0" + Integer.toString(orders_index);
		orders_index++; // 변수 값 증가
		LocalDate date = LocalDate.now(); // 현재 날짜 불러오기
		addOrdersTable(conn, ordersID, customerID, date);
		boolean more = true; // 주문을 계속할 건지 확인하는 변수

		// 케이크 메뉴 및 재고 현황 출력
		showCakeMenu(conn);
		// 케이크 판매 순위 출력
		showBestCake(conn);

		while (more) {
			System.out.print("주문할 케이크 ID를 입력하세요(ex):C001):");
			String cakeID = sc.nextLine();
			if (isValidCakeId(conn, cakeID)) { // 케이크 ID가 DB에 존재하면
				// 주문 계속 진행
				System.out.print("수량을 입력하세요: ");
				int quantity = Integer.parseInt(sc.nextLine());

				// 재고가 존재하는지 확인
				int stock = getAvailableStock(conn, cakeID);
				if (stock >= quantity) {
					// orderitem 테이블에 data 추가
					String orderItemID = "I0" + Integer.toString(orderitem_index);
					addOrderitemTable(conn, orderItemID, ordersID, cakeID, quantity);
					orderitem_index++;

					// 재고 감소
					updateInventoryTable(conn, quantity, cakeID);

					System.out.print("더 주문하시겠습니까? (Y/N): ");
					String answer = sc.nextLine().toUpperCase();
					if (!answer.equals("Y")) {
						more = false;
					}
				} else {
					System.out.println("재고가 부족합니다. 현재 재고: " + stock + "개");
					System.out.println("다시 케이크를 선택해주세요.");
				}
			} else { // 케이크 ID가 DB에 존재하지 않으면
				System.out.println("존재하지 않는 케이크 ID입니다. 다시 입력해주세요.");
			}
		}
	}

	private static void updateInventoryTable(Connection conn, int quantity, String cakeID) {
		// 재고 업데이트 query문
		String sql = "UPDATE inventory SET quantity_available = quantity_available - ? WHERE cake_id = ?";
		try {
			conn.setAutoCommit(false); // 트랜잭션 시작

			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setInt(1, quantity);
				ps.setString(2, cakeID);
				ps.executeUpdate();
			}
			conn.commit(); // 성공 시 커밋

		} catch (SQLException e) {
			try {
				conn.rollback();// 실패시 롤백
				System.out.println("재고 감소에 실패하여 롤백합니다.");
			} catch (SQLException rollbackEx) {
				rollbackEx.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				conn.setAutoCommit(true); // 자동 커밋 복구
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private static int getAvailableStock(Connection conn, String cakeID) {
		// inventory 테이블에서 재고 불러오는 query문
		String sql = "SELECT quantity_available FROM inventory WHERE cake_id = ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, cakeID);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getInt("quantity_available");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0; // 존재하지 않는 경우, 에러가 생긴 경우
	}

	private static void showCakeMenu(Connection conn) {
		// cake_view라는 view에서 정보 가져오는 query문
		String query = "SELECT cake_id, cake_name, price, quantity FROM cake_view";

		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

			// 케이크 메뉴표 출력
			System.out.println("[케이크 메뉴표]");
			System.out.println("-------------------------------------------------");
			System.out.printf("%-10s %-20s %-8s %-5s%n", "케이크ID", "케이크 이름", "가격", "재고");
			System.out.println("-------------------------------------------------");

			while (rs.next()) {
				String id = rs.getString("cake_id");
				String name = rs.getString("cake_name");
				int price = rs.getInt("price");
				int quantity = rs.getInt("quantity");

				System.out.printf("%-10s %-20s %-8d %-5d%n", id, name, price, quantity);
			}
			System.out.println("-------------------------------------------------");

		} catch (SQLException e) {
			System.out.println("케이크 메뉴 조회 실패");
			e.printStackTrace();
		}

	}

	public static void showBestCake(Connection conn) {
		// 가장 많이 팔린 케이크 순위 query 문
		String query = "SELECT c.cake_id, c.cake_name, SUM(oi.quantity) AS total_sold " + "FROM orderitem oi "
				+ "JOIN cake c ON oi.cake_id = c.cake_id " + "GROUP BY c.cake_id, c.cake_name "
				+ "ORDER BY total_sold DESC " + "LIMIT 5";

		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

			System.out.println("\n[가장 많이 팔린 케이크 목록]");
			System.out.println("------------------------------------");
			System.out.printf("%-10s %-20s | %s\n", "케이크ID", "케이크 이름", "판매수량");
			System.out.println("------------------------------------");

			while (rs.next()) {
				String cakeID = rs.getString("cake_id");
				String cakeName = rs.getString("cake_name");
				int totalSold = rs.getInt("total_sold");
				System.out.printf("%-10s %-20s | %d\n", cakeID, cakeName, totalSold);
			}
			System.out.println("------------------------------------");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// case 2 : 전체 주문 목록 조회 - - orders_ID와 orders_date 확인가능
	private static void showCustomerOrder(Connection conn, String customerName) {
		String query = "SELECT orders_id, orders_date FROM customer_order_view WHERE customer_name = ?";

		try (PreparedStatement ps = conn.prepareStatement(query)) {
			ps.setString(1, customerName);
			try (ResultSet rs = ps.executeQuery()) {
				boolean hasResults = false;
				System.out.println(customerName + " 님의 주문 내역:");
				while (rs.next()) {
					String orderId = rs.getString("orders_id");
					Date orderDate = rs.getDate("orders_date");
					System.out.println(" - 주문ID: " + orderId + ", 주문일: " + orderDate);
					hasResults = true;
				}

				if (!hasResults) {
					System.out.println("해당 고객의 주문 내역이 없습니다.");
				}

			}
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("주문 내역 조회 중 오류가 발생했습니다.");
		}
	}

	// case 3 : 현재 주문 내역 상세 - orders_id, 전체 수량, 총 가격
	private static void showOrderDetail(Connection conn, String orderId) {
		String orderInfoSQL = "SELECT o.orders_date, c.customer_name AS customer_name " + "FROM orders o "
				+ "JOIN customer c ON o.customer_id = c.customer_id " + "WHERE o.orders_id = ?";
		String orderItemsSQL = "SELECT ca.cake_id, ca.cake_name AS cake_name, ca.price AS price, SUM(oi.quantity) AS total_quantity, SUM(oi.quantity*ca.price) AS total_price "
				+ "FROM orderitem oi " + "JOIN cake ca on oi.cake_id = ca.cake_id " + "WHERE oi.orders_id = ? "
				+ "GROUP BY ca.cake_name, ca.price, ca.cake_id ";
		try (PreparedStatement orderInfoStmt = conn.prepareStatement(orderInfoSQL);
				PreparedStatement orderItemsStmt = conn.prepareStatement(orderItemsSQL)) {
			// 1. 주문 기본 정보 조회
			orderInfoStmt.setString(1, orderId);
			try (ResultSet orderInfoRs = orderInfoStmt.executeQuery()) {
				if (orderInfoRs.next()) {
					String customerName = orderInfoRs.getString("customer_name");
					Date orderDate = orderInfoRs.getDate("orders_date");
					System.out.println("\n[주문 상세 내역]");
					System.out.println("고객 이름: " + customerName);
					System.out.println("주문 날짜: " + orderDate);
					System.out.println("---------------------------------");
					// 2. 주문한 케이크 목록 조회
					orderItemsStmt.setString(1, orderId);
					try (ResultSet itemRs = orderItemsStmt.executeQuery()) {
						int total = 0;
						while (itemRs.next()) {
							String cakeName = itemRs.getString("cake_name");
							String CakeID = itemRs.getString("cake_id");
							int price = itemRs.getInt("price");
							int quantity = itemRs.getInt("total_quantity");
							int subtotal = itemRs.getInt("total_price");
							total += subtotal;
							System.out.printf("케이크: %s(%s) | 가격: %d | 수량: %d | 소계: %d\n", cakeName, CakeID, price,
									quantity, subtotal);
						}
						System.out.println("---------------------------------");
						System.out.println("[총 주문 금액]: " + total + "원");
					}
				} else {
					System.out.println("해당 주문ID의 정보가 존재하지 않습니다.");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// case 4 : 주문 정보 수정 또는 삭제
	private static void updateOrder(Connection conn) {
		Scanner sc = new Scanner(System.in);

		// 주 번호 조회
		System.out.print("수정 또는 삭제할 주문 ID를 입력하세요 (ex: O001): ");
		String orderId = sc.nextLine();
		System.out.println("1.주문 삭제 ");
		System.out.println("2.주문 정보 수정 (케이크 및 수량 변경)");

		System.out.print("메뉴 선택: ");
		int choice = Integer.parseInt(sc.nextLine());
		// 주문 삭제 선택
		if (choice == 1) {
			try {
				// order item 테이블에서 해당 주문번호 삭제
				String deleteItemsSQL = "DELETE FROM orderitem WHERE orders_id = ?";
				try (PreparedStatement stmt = conn.prepareStatement(deleteItemsSQL)) {
					stmt.setString(1, orderId);
					stmt.executeUpdate();
				}

				// orders 테이블에서 해당 주문번호 삭제
				String deleteOrderSQL = "DELETE FROM orders WHERE orders_id = ?";
				try (PreparedStatement stmt = conn.prepareStatement(deleteOrderSQL)) {
					stmt.setString(1, orderId);
					stmt.executeUpdate();
				}
				System.out.println("주문이 삭제되었습니다.");
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		// 주문 내역 수정 선택
		else if (choice == 2) {
			System.out.print("수정하고 싶은 케이크 ID를 입력하세요 (ex: C001): ");
			String oldCakeId = sc.nextLine();

			System.out.println("새로운 케이크 ID를 입력하세요 (변경하지 않는다면 기존 케이크 ID 입력): ");
			String newCakeId = sc.nextLine();

			if (!isValidCakeId(conn, newCakeId)) {
				System.out.println("케이크 ID가 존재하지 않습니다.");
				return;
			}

			System.out.print("새로운 수량을 입력하세요: ");
			int newQty = Integer.parseInt(sc.nextLine());

			String updateSQL = "UPDATE orderitem SET cake_id = ?, quantity = ? WHERE orders_id = ? AND cake_id = ?";

			try {
				conn.setAutoCommit(false); // 트랜잭션 시작

				try (PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
					stmt.setString(1, newCakeId);
					stmt.setInt(2, newQty);
					stmt.setString(3, orderId);
					stmt.setString(4, oldCakeId);

					int rows = stmt.executeUpdate();
					if (rows > 0) {
						conn.commit(); // 성공 시 커밋
						System.out.println("주문이 수정되었습니다.");
					} else {
						System.out.println("해당 주문 또는 케이크 ID를 찾을 수 없습니다.");
						conn.rollback(); // 실패 시 롤백
					}
				}

			} catch (SQLException e) {
				try {
					conn.rollback(); // 예외 발생 시 롤백
					System.out.println("오류 발생으로 주문 수정 작업을 취소했습니다.");
				} catch (SQLException rollbackEx) {
					rollbackEx.printStackTrace();
				}
				e.printStackTrace();
			} finally {
				try {
					conn.setAutoCommit(true); // 트랜잭션 원복
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

		} else {
			System.out.println("잘못된 선택입니다.");
		}
	}

	// case 5: 고객 정보 삭제 및 수정 기능
	private static void updateCustomer(Connection conn) {
		// TODO Auto-generated method stub
		Scanner sc = new Scanner(System.in);
		String customerId = null;

		while (true) {
			System.out.print("수정 또는 삭제할 고객 ID를 입력하세요(ex: U001, ID 찾기를 원하시면 '검색' 을 입력하세요): ");
			String input = sc.nextLine();

			// ID찾기 선택
			if (input.equalsIgnoreCase("검색")) {
				// 고객 이름으로 ID 검색
				System.out.print("고객 이름을 입력하세요: ");
				String name = sc.nextLine();

				String searchSQL = "SELECT customer_id, customer_name, phone_number FROM customer WHERE customer_name = ?";
				try (PreparedStatement stmt = conn.prepareStatement(searchSQL)) {
					stmt.setString(1, name);
					try (ResultSet rs = stmt.executeQuery()) {
						boolean found = false;
						while (rs.next()) {
							found = true;
							String c_id = rs.getString("customer_id");
							String c_name = rs.getString("customer_name");
							String phone_num = rs.getString("phone_number");

							// 전화번호 가운데 4자리 *로 마스킹 후 customer id, 이름, 전화번호 출력
							String masked_num = phone_num.replaceAll("(\\d{3})-(\\d{4})-(\\d{4})", "$1-****-$3");

							System.out.printf("고객 ID: %s, 이름: %s, 전화번호: %s\n", c_id, c_name, masked_num);
						}
						if (!found) {
							System.out.println("해당 이름의 고객이 존재하지 않습니다.");
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				// ID 검색 이후 다시 고객 ID 입력으로 돌아감
				// 고객 ID 입력시 while문 종료. 다음 단계로 넘어감.
			} else {
				customerId = input;
				break;
			}
		}

		// 고객 ID 확인
		String query = "SELECT customer_name FROM customer WHERE customer_id = ?";
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setString(1, customerId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					System.out.println("해당 고객 ID가 존재하지 않습니다.");
					return;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return;
		}

		// 삭제 또는 수정 선택
		System.out.print("1.고객 정보 삭제 ");
		System.out.println("2.고객 정보 수정");
		int choice = Integer.parseInt(sc.nextLine());

		// 고객 정보 삭제 선택
		if (choice == 1) {
			try {
				// orderitem 에서 해당 고객의 주문 항목 삭제
				String deleteOrderItems = "DELETE FROM orderitem WHERE orders_id IN (SELECT orders_id FROM orders WHERE customer_id = ?)";
				try (PreparedStatement stmt = conn.prepareStatement(deleteOrderItems)) {
					stmt.setString(1, customerId);
					stmt.executeUpdate();
				}

				// orders 에서 해당 고객의 주문 정보 삭제
				String deleteOrders = "DELETE FROM orders WHERE customer_id = ?";
				try (PreparedStatement stmt = conn.prepareStatement(deleteOrders)) {
					stmt.setString(1, customerId);
					stmt.executeUpdate();
				}

				// customer 에서 해당 고객 정보 삭제
				String deleteCustomer = "DELETE FROM customer WHERE customer_id = ?";
				try (PreparedStatement stmt = conn.prepareStatement(deleteCustomer)) {
					stmt.setString(1, customerId);
					stmt.executeUpdate();
				}

				System.out.println("고객 정보 및 관련 주문이 모두 삭제되었습니다.");
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		// 고객 정보 수정 선택
		else if (choice == 2) {
			System.out.print("새 이름을 입력하세요(변경하지 않는다면 동일한 이름 입력): "); // 이름 수정
			String newName = sc.nextLine();
			System.out.print("새 전화번호를 입력하세요(변경하지 않는다면 동일한 번호 입력): "); // 전화번호 수정
			String newPhone = sc.nextLine();

			String updateSQL = "UPDATE customer SET customer_name = ?, phone_number = ? WHERE customer_id = ?"; // 테이블에
																												// 수정사항
																												// 업데이트
			try {
				conn.setAutoCommit(false); // 트랜잭션 시작

				try (PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
					stmt.setString(1, newName);
					stmt.setString(2, newPhone);
					stmt.setString(3, customerId);
					int rows = stmt.executeUpdate();

					if (rows > 0) {
						conn.commit(); // 성공 시 커밋
						System.out.println("고객 정보가 수정되었습니다.");
					} else {
						System.out.println("고객 정보를 찾을 수 없습니다.");
						conn.rollback(); // 실패 시 롤백
					}
				}

			} catch (SQLException e) {
				try {
					conn.rollback();
					System.out.println("오류 발생으로 고객 정보 수정 작업을 취소했습니다.");
				} catch (SQLException rollbackEx) {
					rollbackEx.printStackTrace();
				}
				e.printStackTrace();
			} finally {
				try {
					conn.setAutoCommit(true); // 자동 커밋 복원
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// 입력한 cakeID가 DB에 존재하는지 확인하는 함수
	public static boolean isValidCakeId(Connection conn, String cakeId) {
		String query = "SELECT 1 FROM cake WHERE cake_id = ?";
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setString(1, cakeId);
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next(); // 존재하면 true
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	// 이름 입력을 통해 customerID를 찾고, 없을 경우 고객 정보 추가하는 함수
	public static String findCustomerID(Connection conn, String name) {
		Scanner sc = new Scanner(System.in);
		String query = "SELECT customer_id FROM customer WHERE customer_name = ?"; // 고객 테이블에 name이 아니라 customer_name으로
																					// 되어있어서 수정했습니다.
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setString(1, name);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) { // 고객 정보가 존재할 경우
					return rs.getString("customer_id");
				} else { // 고객 정보가 존재하지 않을 경우
					System.out.println("고객 정보가 존재하지 않아 새로 추가합니다.");
					System.out.print("전화번호를 입력하세요(ex)010-1111-2222): ");
					String phone_num = sc.nextLine();
					String customerID = "U0" + Integer.toString(customer_index);
					customer_index++; // 변수 값 증가 -> 이후에 적용
					// DB customer 테이블에 data 추가가
					addCustomerTable(conn, customerID, name, phone_num);
					return customerID;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	// customer 테이블에 데이터 추가 함수
	private static void addCustomerTable(Connection conn, String customerID, String name, String phone_num) {
		// customer 테이블 insert query문
		String sql = "INSERT INTO customer (customer_id,  customer_name, phone_number) VALUES (?, ?, ?)";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, customerID);
			stmt.setString(2, name);
			stmt.setString(3, phone_num);
			stmt.executeUpdate();
			System.out.println("고객 정보가 등록되었습니다.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// orders 테이블에 데이터 추가 함수
	private static void addOrdersTable(Connection conn, String ordersID, String customerID, LocalDate date) {
		// orders 테이블에 데이터 insert query문
		String sql = "INSERT INTO orders (orders_id, customer_id, orders_date) VALUES (?, ?, ?)"; // OrderItem에 추가하는 쿼리로
																									// 돼있어서 수정했습니다.
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, ordersID);
			stmt.setString(2, customerID);
			stmt.setDate(3, Date.valueOf(date));
			stmt.executeUpdate();

			System.out.println("주문번호가 생성되었습니다.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// orderitem 테이블에 데이터 추가 함수
	private static void addOrderitemTable(Connection conn, String orderItemID, String ordersID, String cakeID,
			int quantity) {
		// orderitem 테이블에 데이터 insert 함수
		String sql = "INSERT INTO orderitem (order_item_id, orders_id, cake_id, quantity) VALUES (?, ?, ?, ?)";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, orderItemID);
			stmt.setString(2, ordersID);
			stmt.setString(3, cakeID);
			stmt.setInt(4, quantity);
			stmt.executeUpdate();

			System.out.println("주문이 완료되었습니다.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Connection conn = sql_connection.getConnection(); // DB 연결

		SQLExecutor.executeSQLFile(conn, "src/sql/dropschema.sql"); // drop 테이블
		SQLExecutor.executeSQLFile(conn, "src/sql/createschema.sql"); // 테이블 만들기
		SQLExecutor.executeSQLFile(conn, "src/sql/initdata.sql"); // 데이터 넣기

		초기화면();
		메뉴(conn); // DB 연결 이어서 가져감
		sql_connection.close();
	}
}
