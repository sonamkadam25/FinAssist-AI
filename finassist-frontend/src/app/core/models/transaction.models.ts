export type TransactionType = 'INCOME' | 'EXPENSE';
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface Transaction {
  id: number;
  transactionType: TransactionType;
  amount: number;
  category: string;
  merchant: string;
  description: string | null;
  transactionDate: string;
  status: TransactionStatus;
  createdAt: string;
}

export interface TransactionInput {
  transactionType: TransactionType;
  amount: number;
  category: string;
  merchant: string;
  description: string | null;
  transactionDate: string;
  status: TransactionStatus;
}

export interface TransactionPage {
  content: Transaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface CategorySummary {
  category: string;
  totalAmount: number;
}

export interface TransactionSummary {
  totalIncome: number;
  totalExpenses: number;
  netBalance: number;
  totalTransactions: number;
  completedTransactions: number;
  pendingTransactions: number;
  failedTransactions: number;
  expenseTotalsByCategory: CategorySummary[];
  incomeTotalsByCategory: CategorySummary[];
}

export interface TransactionFilters {
  minAmount?: number;
  maxAmount?: number;
  status?: TransactionStatus | '';
  transactionType?: TransactionType | '';
  category?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

export interface TransactionDataItem {
  id: number;
  transactionType: TransactionType;
  amount: number;
  category: string;
  transactionDate: string;
  status: TransactionStatus;
}

export interface TransactionSearchData {
  transactions: TransactionDataItem[];
  totalMatches: number;
  returnedCount: number;
  truncated: boolean;
}

export interface TransactionSummaryData {
  startDate: string | null;
  endDate: string | null;
  statistics: TransactionSummary;
}

export interface AskTransactionData {
  summary: TransactionSummaryData | null;
  search: TransactionSearchData | null;
}

export interface AskResponse {
  question: string;
  answer: string;
  data: AskTransactionData | null;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  details: Record<string, string>;
}
