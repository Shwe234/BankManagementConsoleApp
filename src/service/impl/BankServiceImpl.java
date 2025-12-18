package service.impl;

import domain.Account;
import domain.Customer;
import domain.Transaction;
import domain.Type;
import exceptions.accountNotFoundException;
import exceptions.insufficientFundsException;
import exceptions.validationException;
import repositry.AccountRepository;
import repositry.TransactionRepository;
import repositry.CustomerRepository;
import service.BankService;
import util.Validations;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BankServiceImpl implements BankService {

    private final AccountRepository accountRepository = new AccountRepository();
    private final TransactionRepository transactionRepository = new TransactionRepository();
    private final CustomerRepository customerRepository = new CustomerRepository();

    private final Validations<String> validateName = name -> {
        if (name == null || name.isBlank()) throw new validationException("Name is required");
    };

    private final Validations<String> validateEmail = email -> {
        if (email == null || !email.contains("@")) throw new validationException("Email is required");
    };

    private final Validations<String> validateType = type -> {
        if (type == null || !(type.equalsIgnoreCase("SAVINGS") || type.equalsIgnoreCase("CURRENT")))
            throw new validationException("Type must be SAVINGS or CURRENT");
    };

    private final Validations<Double> validateAmountPositive = amount -> {
        if (amount == null || amount <= 0)
            throw new validationException("Please enter valid amount");
    };

    @Override
    public String openAccount(String name, String email, String accountType) {
        validateName.validate(name);
        validateEmail.validate(email);
        validateType.validate(accountType);

        String customerId = UUID.randomUUID().toString();
        Customer c = new Customer(customerId, name, email);
        customerRepository.save(c);

        String accountNumber = getAccountNumber();
        Account account = new Account(accountNumber, accountType.toUpperCase(), 0.0, customerId);
        accountRepository.save(account);

        return accountNumber;
    }

    @Override
    public List<Account> listAccounts() {
        return accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getAccountNumber))
                .collect(Collectors.toList());
    }

    @Override
    public void deposit(String accountNumber, Double amount, String note) {
        validateAmountPositive.validate(amount);
        Account account = accountRepository.findByNumber(accountNumber)
                .orElseThrow(() -> new accountNotFoundException("Account not found: " + accountNumber));

        account.setBalance(account.getBalance() + amount);
        Transaction transaction = new Transaction(account.getAccountNumber(),
                amount, UUID.randomUUID().toString(), note, LocalDateTime.now(), Type.DEPOSIT);
        transactionRepository.add(transaction);
    }

    @Override
    public void withdraw(String accountNumber, Double amount, String note) {
        validateAmountPositive.validate(amount);
        Account account = accountRepository.findByNumber(accountNumber)
                .orElseThrow(() -> new accountNotFoundException("Account not found: " + accountNumber));

        if (account.getBalance() < amount)
            throw new insufficientFundsException("Insufficient Balance");

        account.setBalance(account.getBalance() - amount);
        Transaction transaction = new Transaction(account.getAccountNumber(),
                amount, UUID.randomUUID().toString(), note, LocalDateTime.now(), Type.WITHDRAW);
        transactionRepository.add(transaction);
    }

    @Override
    public void transfer(String fromAcc, String toAcc, Double amount, String note) {
        validateAmountPositive.validate(amount);
        if (fromAcc.equals(toAcc))
            throw new validationException("Cannot transfer to your own account");

        Account from = accountRepository.findByNumber(fromAcc)
                .orElseThrow(() -> new accountNotFoundException("Account not found: " + fromAcc));
        Account to = accountRepository.findByNumber(toAcc)
                .orElseThrow(() -> new accountNotFoundException("Account not found: " + toAcc));

        if (from.getBalance() < amount)
            throw new insufficientFundsException("Insufficient Balance");

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);

        transactionRepository.add(new Transaction(from.getAccountNumber(),
                amount, UUID.randomUUID().toString(), note, LocalDateTime.now(), Type.TRANSFER_OUT));

        transactionRepository.add(new Transaction(to.getAccountNumber(),
                amount, UUID.randomUUID().toString(), note, LocalDateTime.now(), Type.TRANSFER_IN));
    }

    @Override
    public List<Transaction> getStatement(String account) {
        return transactionRepository.findByAccount(account).stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp))
                .collect(Collectors.toList());
    }

    @Override
    public List<Account> searchAccountsByCustomerName(String q) {
        String query = (q == null) ? "" : q.toLowerCase();
        return customerRepository.findAll().stream()
                .filter(c -> c.getName().toLowerCase().contains(query))
                .flatMap(c -> accountRepository.findByCustomerId(c.getId()).stream())
                .sorted(Comparator.comparing(Account::getAccountNumber))
                .collect(Collectors.toList());
    }

    private String getAccountNumber() {
        int size = accountRepository.findAll().size() + 1;
        return String.format("AC%06d", size);
    }
}
