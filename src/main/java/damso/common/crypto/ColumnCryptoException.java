package damso.common.crypto;

/** 컬럼 암복호화 실패. 메시지는 고정 문구이며 입력·암호문·평문을 담지 않는다. */
public class ColumnCryptoException extends RuntimeException {

	ColumnCryptoException(String message) {
		super(message);
	}

	ColumnCryptoException(String message, Throwable cause) {
		super(message, cause);
	}

}
