import boto3
from botocore.exceptions import ClientError
import os
from dotenv import load_dotenv

load_dotenv()

class S3Service:
    def __init__(self):
        self.s3_client = boto3.client(
            's3',
            region_name=os.getenv('AWS_REGION'),
            aws_access_key_id=os.getenv('AWS_ACCESS_KEY_ID'),
            aws_secret_access_key=os.getenv('AWS_SECRET_ACCESS_KEY')
        )
        self.bucket_name = os.getenv('S3_BUCKET_NAME')
    
    def generate_presigned_url(self, s3_key: str, expires_in: int = 300) -> str:
        """Presigned URL 생성"""
        try:
            url = self.s3_client.generate_presigned_url(
                'put_object',
                Params={
                    'Bucket': self.bucket_name,
                    'Key': s3_key,
                    'ContentType': 'video/mp4'
                },
                ExpiresIn=expires_in
            )
            return url
        except ClientError as e:
            raise Exception(f"S3 URL 생성 실패: {str(e)}")
    
    def upload_file(self, file_path: str, s3_key: str):
        """파일을 S3에 업로드"""
        try:
            self.s3_client.upload_file(file_path, self.bucket_name, s3_key)
            return f"https://{self.bucket_name}.s3.{os.getenv('AWS_REGION')}.amazonaws.com/{s3_key}"
        except ClientError as e:
            raise Exception(f"S3 업로드 실패: {str(e)}")

# 싱글톤 인스턴스
s3_service = S3Service()